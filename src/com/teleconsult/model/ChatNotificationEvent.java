package com.teleconsult.model;

import java.util.Collections;
import java.util.Map;

/**
 * Événement CDI déclenché quand un message ou un document est ajouté à une
 * consultation. Observé par ConsultationRoomManager, qui le relaie aux
 * participants connectés sous forme de message WebSocket CHAT_NOTIFY.
 * (Découple les services métier de la couche WebSocket.)
 */
public final class ChatNotificationEvent {

	private final int consultationId;
	private final String senderId;
	private final Map<String, Object> payload;

	public ChatNotificationEvent(int consultationId, String senderId, Map<String, Object> payload) {
		this.consultationId = consultationId;
		this.senderId = senderId;
		this.payload = Collections.unmodifiableMap(payload);
	}

	public int getConsultationId() {
		return consultationId;
	}

	public String getSenderId() {
		return senderId;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}
}
