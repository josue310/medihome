package com.teleconsult.dto;

/**
 * Réponse de GET /api/consultations/{id}/context : tout ce dont la page cliente
 * a besoin pour démarrer (identité à utiliser comme senderId, jeton CSRF pour
 * les POST, limites). Lisible uniquement par la page de même origine (pas de CORS).
 */
public class ConsultationContextDto {

	private int consultationId;
	private String userId;
	private String role;
	private String displayName;
	private String csrfToken;
	private long maxUploadBytes;
	private int maxMessageLength;

	public ConsultationContextDto(int consultationId, String userId, String role, String displayName,
			String csrfToken, long maxUploadBytes, int maxMessageLength) {
		this.consultationId = consultationId;
		this.userId = userId;
		this.role = role;
		this.displayName = displayName;
		this.csrfToken = csrfToken;
		this.maxUploadBytes = maxUploadBytes;
		this.maxMessageLength = maxMessageLength;
	}

	public int getConsultationId() {
		return consultationId;
	}

	public String getUserId() {
		return userId;
	}

	public String getRole() {
		return role;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getCsrfToken() {
		return csrfToken;
	}

	public long getMaxUploadBytes() {
		return maxUploadBytes;
	}

	public int getMaxMessageLength() {
		return maxMessageLength;
	}
}
