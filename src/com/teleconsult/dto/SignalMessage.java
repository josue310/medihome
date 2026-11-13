package com.teleconsult.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Enveloppe JSON de TOUS les messages WebSocket (contrat strict) :
 *
 * <pre>
 * { "type": "CALL_REQUEST", "consultationId": "12345", "senderId": "PATIENT-3",
 *   "timestamp": 1730000000000, "payload": { } }
 * </pre>
 *
 * Toute propriété inconnue est refusée au décodage (voir SignalMessageCodec).
 */
@JsonPropertyOrder({ "type", "consultationId", "senderId", "timestamp", "payload" })
public class SignalMessage {

	/** senderId utilisé pour les messages émis par le serveur lui-même. */
	public static final String SERVER_SENDER = "SERVER";

	private String type;
	private String consultationId;
	private String senderId;
	private long timestamp;
	private JsonNode payload;

	public SignalMessage() {
	}

	public SignalMessage(String type, String consultationId, String senderId, long timestamp, JsonNode payload) {
		this.type = type;
		this.consultationId = consultationId;
		this.senderId = senderId;
		this.timestamp = timestamp;
		this.payload = payload == null ? JsonNodeFactory.instance.objectNode() : payload;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getConsultationId() {
		return consultationId;
	}

	public void setConsultationId(String consultationId) {
		this.consultationId = consultationId;
	}

	public String getSenderId() {
		return senderId;
	}

	public void setSenderId(String senderId) {
		this.senderId = senderId;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public JsonNode getPayload() {
		return payload;
	}

	public void setPayload(JsonNode payload) {
		this.payload = payload;
	}
}
