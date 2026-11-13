package com.teleconsult.websocket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.teleconsult.model.ParticipantState;
import com.teleconsult.model.Role;

/**
 * Salle d'une consultation : au plus 2 participants (un MEDECIN, un PATIENT)
 * et l'état de l'appel 1-à-1.
 *
 * NON thread-safe en soi : tout accès se fait sous synchronized(room), ce qui
 * rend chaque transition d'état atomique (pas de double CALL_ACCEPT, etc.).
 */
final class ConsultationRoom {

	final int consultationId;
	private final EnumMap<Role, RoomParticipant> participants = new EnumMap<Role, RoomParticipant>(Role.class);

	/** Vrai quand la salle vide a été retirée du gestionnaire : ne plus l'utiliser. */
	boolean closed;

	// --- État de l'appel (callId == null : aucun appel) ---
	String callId;
	Role callerRole;
	/** 0 tant que ça sonne ; horodatage du CALL_ACCEPT ensuite. */
	long callStartedAt;
	/** Identifiants figés au CALL_REQUEST (l'un peut être temporairement déconnecté). */
	String callerId;
	String calleeId;

	// --- Reconnexion : participant en appel dont la WebSocket a été coupée (1006) ---
	/** Rôle dont la connexion est suspendue (null : aucune suspension). */
	Role suspendedRole;
	String suspendedUserId;
	/** Instant de la coupure : sert d'heure de fin d'audit si le participant ne revient pas. */
	long suspendedAt;
	/** Incrémenté à chaque suspension/reprise : invalide les tâches d'expiration obsolètes. */
	long suspendToken;

	ConsultationRoom(int consultationId) {
		this.consultationId = consultationId;
	}

	RoomParticipant get(Role role) {
		return participants.get(role);
	}

	RoomParticipant peerOf(RoomParticipant p) {
		return participants.get(p.getRole().other());
	}

	/** Ajoute le participant ; retourne la connexion qu'il remplace (même rôle) ou null. */
	RoomParticipant put(RoomParticipant p) {
		return participants.put(p.getRole(), p);
	}

	void remove(Role role) {
		participants.remove(role);
	}

	boolean isEmpty() {
		return participants.isEmpty();
	}

	/** Copie de la liste : on peut itérer même si la salle change ensuite. */
	List<RoomParticipant> snapshot() {
		return new ArrayList<RoomParticipant>(participants.values());
	}

	boolean isRinging() {
		return callId != null && callStartedAt == 0;
	}

	boolean isInCall() {
		return callId != null && callStartedAt > 0;
	}

	/** Fin d'appel : retour en salle d'attente pour tous. */
	void resetCall() {
		callId = null;
		callerRole = null;
		callStartedAt = 0;
		callerId = null;
		calleeId = null;
		clearSuspension();
		for (RoomParticipant p : participants.values()) {
			p.setState(ParticipantState.WAITING);
		}
	}

	boolean isSuspended() {
		return suspendedRole != null;
	}

	void clearSuspension() {
		suspendedRole = null;
		suspendedUserId = null;
		suspendedAt = 0;
		suspendToken++;
	}
}
