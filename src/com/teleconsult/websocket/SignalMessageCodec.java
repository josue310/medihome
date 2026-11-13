package com.teleconsult.websocket;

import java.io.IOException;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teleconsult.dto.SignalMessage;
import com.teleconsult.model.MessageType;

/**
 * Sérialisation JSON (Jackson) des messages de signalisation. ObjectMapper est
 * thread-safe une fois configuré : une seule instance pour toute l'application.
 */
@ApplicationScoped
public class SignalMessageCodec {

	private final ObjectMapper mapper = new ObjectMapper()
			// contrat strict : toute propriété inconnue dans l'enveloppe est refusée
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

	public SignalMessage decode(String json) throws IOException {
		return mapper.readValue(json, SignalMessage.class);
	}

	public String encode(SignalMessage message) {
		try {
			return mapper.writeValueAsString(message);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Serialization of signal message failed", e);
		}
	}

	/** Construit et sérialise un message, horodaté par le serveur. */
	public String message(MessageType type, int consultationId, String senderId, JsonNode payload) {
		return encode(new SignalMessage(type.name(), String.valueOf(consultationId), senderId,
				System.currentTimeMillis(), payload));
	}

	public ObjectNode object() {
		return mapper.createObjectNode();
	}

	public JsonNode toTree(Map<String, Object> values) {
		return mapper.valueToTree(values);
	}
}
