package com.teleconsult.model;

/**
 * Identité vérifiée d'un participant pour UNE consultation (immuable, donc
 * partageable entre threads). Produite uniquement par ConsultationSecurityService.
 */
public final class AuthenticatedParticipant {

	private final int consultationId;
	private final Role role;
	private final int accountId;
	private final String displayName;

	public AuthenticatedParticipant(int consultationId, Role role, int accountId, String displayName) {
		this.consultationId = consultationId;
		this.role = role;
		this.accountId = accountId;
		this.displayName = displayName;
	}

	public int getConsultationId() {
		return consultationId;
	}

	public Role getRole() {
		return role;
	}

	/** Identifiant du compte dans sa table (doctor.id ou user_dtls.user_id). */
	public int getAccountId() {
		return accountId;
	}

	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Identifiant unique utilisé dans le protocole (senderId) : « MEDECIN-3 »,
	 * « PATIENT-7 ». Le préfixe évite toute collision entre les identifiants
	 * des tables doctor et user_dtls.
	 */
	public String getUserId() {
		return role.name() + "-" + accountId;
	}

	@Override
	public String toString() {
		return getUserId() + "@consultation-" + consultationId;
	}
}
