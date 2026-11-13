package com.teleconsult.service;

/**
 * Erreur métier du module, avec un code stable (exploitable par le client)
 * et le statut HTTP correspondant. Traduite en JSON par l'API REST et en
 * motif de refus lors de la poignée de main WebSocket.
 */
public class TeleconsultationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final int httpStatus;
	private final String code;

	public TeleconsultationException(int httpStatus, String code, String message) {
		super(message);
		this.httpStatus = httpStatus;
		this.code = code;
	}

	public TeleconsultationException(int httpStatus, String code, String message, Throwable cause) {
		super(message, cause);
		this.httpStatus = httpStatus;
		this.code = code;
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public String getCode() {
		return code;
	}

	public static TeleconsultationException unauthorized(String message) {
		return new TeleconsultationException(401, "NOT_AUTHENTICATED", message);
	}

	public static TeleconsultationException forbidden(String code, String message) {
		return new TeleconsultationException(403, code, message);
	}

	public static TeleconsultationException badRequest(String code, String message) {
		return new TeleconsultationException(400, code, message);
	}

	public static TeleconsultationException payloadTooLarge(String message) {
		return new TeleconsultationException(413, "PAYLOAD_TOO_LARGE", message);
	}

	public static TeleconsultationException unsupportedMediaType(String message) {
		return new TeleconsultationException(415, "UNSUPPORTED_MEDIA_TYPE", message);
	}

	public static TeleconsultationException internal(String message, Throwable cause) {
		return new TeleconsultationException(500, "INTERNAL_ERROR", message, cause);
	}
}
