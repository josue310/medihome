package com.teleconsult.model;

/**
 * Types de messages du protocole de signalisation.
 * clientAllowed = false : message émis uniquement par le serveur ; un client
 * qui tente de l'envoyer reçoit une erreur FORBIDDEN_TYPE.
 */
public enum MessageType {
	// Cycle d'appel
	CALL_REQUEST(true), CALL_ACCEPT(true), CALL_REJECT(true), CALL_CANCEL(true), HANGUP(true),
	// Négociation WebRTC
	SDP_OFFER(true), SDP_ANSWER(true), ICE_CANDIDATE(true), ICE_RESTART(true),
	// Émis par le serveur uniquement
	CHAT_NOTIFY(false), BUSY(false), PARTICIPANT_JOINED(false), PARTICIPANT_LEFT(false), ROOM_STATE(false),
	ERROR(false);

	private final boolean clientAllowed;

	MessageType(boolean clientAllowed) {
		this.clientAllowed = clientAllowed;
	}

	public boolean isClientAllowed() {
		return clientAllowed;
	}

	/** Conversion stricte (sensible à la casse) ; null si inconnu. */
	public static MessageType fromString(String value) {
		if (value == null) {
			return null;
		}
		for (MessageType t : values()) {
			if (t.name().equals(value)) {
				return t;
			}
		}
		return null;
	}
}
