package com.teleconsult.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpSession;

import com.entity.Doctor;
import com.entity.User;
import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.model.Role;

/**
 * Contrôle d'accès aux consultations. Une consultation correspond à un rendez-vous
 * (table appointment) : seuls le médecin (doctor_id) et le patient (user_id)
 * de ce rendez-vous peuvent y accéder.
 *
 * L'identité provient EXCLUSIVEMENT de la session HTTP authentifiée (attributs
 * doctObj / userObj posés par les servlets de connexion), jamais d'un paramètre
 * fourni par le client.
 */
@ApplicationScoped
public class ConsultationSecurityService {

	private static final Logger LOG = Logger.getLogger(ConsultationSecurityService.class.getName());

	static final String DOCTOR_ATTR = "doctObj";
	static final String PATIENT_ATTR = "userObj";

	@Inject
	private Database database;

	/**
	 * @param session         session HTTP (peut être null)
	 * @param consultationRaw identifiant de consultation tel que reçu (chemin d'URL)
	 * @param requestedRole   rôle demandé (optionnel ; utile seulement si la même
	 *                        session est connectée à la fois comme médecin et patient)
	 * @throws TeleconsultationException 400/401/403 si l'accès est refusé
	 */
	public AuthenticatedParticipant authenticate(HttpSession session, String consultationRaw, String requestedRole) {
		if (session == null) {
			throw TeleconsultationException.unauthorized("No authenticated session");
		}
		int consultationId = parseConsultationId(consultationRaw);

		Object doctor;
		Object patient;
		try {
			doctor = session.getAttribute(DOCTOR_ATTR);
			patient = session.getAttribute(PATIENT_ATTR);
		} catch (IllegalStateException invalidated) {
			throw TeleconsultationException.unauthorized("Session expired");
		}
		if (!(doctor instanceof Doctor) && !(patient instanceof User)) {
			throw TeleconsultationException.unauthorized("No authenticated user in session");
		}

		int[] owners = loadConsultationOwners(consultationId); // {user_id, doctor_id} ou null
		List<AuthenticatedParticipant> candidates = new ArrayList<AuthenticatedParticipant>(2);
		if (owners != null) {
			if (doctor instanceof Doctor && ((Doctor) doctor).getId() == owners[1]) {
				Doctor d = (Doctor) doctor;
				candidates.add(new AuthenticatedParticipant(consultationId, Role.MEDECIN, d.getId(), d.getFullName()));
			}
			if (patient instanceof User && ((User) patient).getId() == owners[0]) {
				User u = (User) patient;
				candidates.add(new AuthenticatedParticipant(consultationId, Role.PATIENT, u.getId(), u.getFullName()));
			}
		}
		if (candidates.isEmpty()) {
			// Même réponse que la consultation existe ou non : pas d'énumération possible.
			LOG.warning("Accès refusé à la consultation " + consultationId);
			throw TeleconsultationException.forbidden("NOT_A_PARTICIPANT",
					"You are not a participant of this consultation");
		}

		Role wanted = Role.parse(requestedRole);
		if (requestedRole != null && !requestedRole.isEmpty() && wanted == null) {
			throw TeleconsultationException.badRequest("INVALID_ROLE", "role must be MEDECIN or PATIENT");
		}
		if (wanted != null) {
			for (AuthenticatedParticipant c : candidates) {
				if (c.getRole() == wanted) {
					return c;
				}
			}
			throw TeleconsultationException.forbidden("NOT_A_PARTICIPANT",
					"You are not a participant of this consultation with role " + wanted);
		}
		if (candidates.size() > 1) {
			throw TeleconsultationException.badRequest("ROLE_REQUIRED",
					"Session is both doctor and patient of this consultation: specify ?role=");
		}
		return candidates.get(0);
	}

	/**
	 * Vérifie que la session HTTP est toujours valide et toujours connectée avec
	 * la même identité (déconnexion, expiration...). Utilisé périodiquement pour
	 * fermer les WebSockets dont l'utilisateur s'est déconnecté.
	 */
	public boolean isStillAuthenticated(HttpSession session, AuthenticatedParticipant p) {
		if (session == null) {
			return false;
		}
		try {
			if (p.getRole() == Role.MEDECIN) {
				Object d = session.getAttribute(DOCTOR_ATTR);
				return d instanceof Doctor && ((Doctor) d).getId() == p.getAccountId();
			}
			Object u = session.getAttribute(PATIENT_ATTR);
			return u instanceof User && ((User) u).getId() == p.getAccountId();
		} catch (IllegalStateException invalidated) {
			return false;
		}
	}

	private int parseConsultationId(String raw) {
		try {
			int id = Integer.parseInt(raw == null ? "" : raw.trim());
			if (id > 0) {
				return id;
			}
		} catch (NumberFormatException ignored) {
			// traité ci-dessous
		}
		throw TeleconsultationException.badRequest("INVALID_CONSULTATION_ID", "Invalid consultation id");
	}

	private int[] loadConsultationOwners(int consultationId) {
		Connection c = null;
		try {
			c = database.getConnection();
			PreparedStatement ps = c.prepareStatement("select user_id, doctor_id from appointment where id=?");
			ps.setInt(1, consultationId);
			ResultSet rs = ps.executeQuery();
			return rs.next() ? new int[] { rs.getInt(1), rs.getInt(2) } : null;
		} catch (SQLException e) {
			LOG.log(Level.SEVERE, "Vérification d'accès impossible (base de données)", e);
			throw TeleconsultationException.internal("Database unavailable", e);
		} finally {
			if (c != null) {
				try {
					c.close();
				} catch (SQLException ignored) {
					// fermeture best-effort
				}
			}
		}
	}
}
