package com.teleconsult.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teleconsult.dto.SignalMessage;
import com.teleconsult.model.CallEndReason;
import com.teleconsult.model.MessageType;
import com.teleconsult.model.ParticipantState;
import com.teleconsult.service.CallAuditService;

/**
 * Validation et routage des messages de signalisation reçus d'un client.
 *
 * Validation de l'enveloppe (toute erreur -> message ERROR à l'émetteur seul) :
 * - JSON conforme au contrat strict (aucune propriété inconnue) ;
 * - type connu et autorisé côté client (CHAT_NOTIFY, BUSY... sont réservés au serveur) ;
 * - consultationId identique à celui de la connexion ;
 * - senderId identique à l'identité établie lors de la poignée de main
 *   (session HTTP) : un client ne peut pas usurper l'autre participant ;
 * - payload objet JSON, avec les champs requis selon le type.
 *
 * Machine à états de l'appel (transitions atomiques sous synchronized(room)) :
 *
 * <pre>
 *  WAITING --CALL_REQUEST--> RINGING --CALL_ACCEPT--> IN_CALL --HANGUP--> WAITING
 *                               |  \--CALL_REJECT (appelé)--> WAITING
 *                               \----CALL_CANCEL (appelant, timeout)--> WAITING
 * </pre>
 *
 * Les messages relayés sont ré-horodatés par le serveur.
 */
@ApplicationScoped
public class SignalingRouter {

	private static final Logger LOG = Logger.getLogger(SignalingRouter.class.getName());

	/** Taille max d'un message (une offre SDP vidéo fait typiquement 3 à 10 Ko). */
	public static final int MAX_MESSAGE_CHARS = 64 * 1024;

	@Inject
	private SignalMessageCodec codec;

	@Inject
	private CallAuditService audit;

	public void route(RoomParticipant sender, String raw) {
		sender.markAlive();
		if (raw == null || raw.length() > MAX_MESSAGE_CHARS) {
			sendError(sender, null, "MESSAGE_TOO_LARGE", "Message exceeds " + MAX_MESSAGE_CHARS + " characters");
			return;
		}
		SignalMessage msg;
		try {
			msg = codec.decode(raw);
		} catch (IOException e) {
			sendError(sender, null, "INVALID_MESSAGE", "Malformed or non-conforming JSON envelope");
			return;
		}
		MessageType type = MessageType.fromString(msg.getType());
		if (!validateEnvelope(sender, msg, type)) {
			return;
		}

		ConsultationRoom room = sender.getRoom();
		List<Runnable> afterUnlock = new ArrayList<Runnable>();
		synchronized (room) {
			if (room.closed || room.get(sender.getRole()) != sender) {
				return; // connexion remplacée entre-temps : message ignoré
			}
			dispatch(room, sender, type, msg.getPayload(), afterUnlock);
		}
		for (Runnable r : afterUnlock) {
			try {
				r.run();
			} catch (RuntimeException e) {
				LOG.log(Level.SEVERE, "Post-transition action failed", e);
			}
		}
	}

	private boolean validateEnvelope(RoomParticipant sender, SignalMessage msg, MessageType type) {
		if (type == null) {
			sendError(sender, null, "UNKNOWN_TYPE", "Unknown message type");
			return false;
		}
		if (!type.isClientAllowed()) {
			sendError(sender, type, "FORBIDDEN_TYPE", type + " can only be emitted by the server");
			return false;
		}
		if (!String.valueOf(sender.getIdentity().getConsultationId()).equals(msg.getConsultationId())) {
			sendError(sender, type, "CONSULTATION_MISMATCH", "consultationId does not match this connection");
			return false;
		}
		if (!sender.getUserId().equals(msg.getSenderId())) {
			LOG.warning("Tentative d'usurpation : " + sender + " a envoyé senderId=" + msg.getSenderId());
			sendError(sender, type, "SENDER_MISMATCH", "senderId does not match the authenticated user");
			return false;
		}
		if (msg.getTimestamp() <= 0) {
			sendError(sender, type, "INVALID_TIMESTAMP", "timestamp must be a positive epoch in milliseconds");
			return false;
		}
		JsonNode payload = msg.getPayload();
		if (payload == null || payload.isNull()) {
			msg.setPayload(codec.object());
		} else if (!payload.isObject()) {
			sendError(sender, type, "INVALID_PAYLOAD", "payload must be a JSON object");
			return false;
		}
		String missing = missingField(type, msg.getPayload());
		if (missing != null) {
			sendError(sender, type, "INVALID_PAYLOAD", "payload." + missing + " is required for " + type);
			return false;
		}
		return true;
	}

	/** Champs obligatoires du payload selon le type (null si tout est présent). */
	private static String missingField(MessageType type, JsonNode payload) {
		switch (type) {
		case SDP_OFFER:
		case SDP_ANSWER:
			JsonNode sdp = payload.get("sdp");
			return sdp != null && sdp.isTextual() && !sdp.asText().isEmpty() ? null : "sdp";
		case ICE_CANDIDATE:
			// chaîne vide autorisée : signale la fin des candidats (end-of-candidates)
			JsonNode cand = payload.get("candidate");
			return cand != null && cand.isTextual() ? null : "candidate";
		default:
			return null;
		}
	}

	/** Exécuté sous le verrou de la salle. */
	private void dispatch(ConsultationRoom room, RoomParticipant sender, MessageType type, JsonNode payload,
			List<Runnable> afterUnlock) {
		RoomParticipant peer = room.peerOf(sender);
		boolean isCaller = room.callId != null && room.callerRole == sender.getRole();

		if (peer == null && room.isSuspended() && type != MessageType.HANGUP) {
			// Correspondant momentanément déconnecté (délai de grâce) : le client patiente.
			sendError(sender, type, "PEER_RECONNECTING", "The other participant is reconnecting");
			return;
		}

		switch (type) {
		case CALL_REQUEST:
			if (peer == null) {
				sendError(sender, type, "PEER_NOT_CONNECTED", "The other participant is not in the waiting room");
				return;
			}
			if (peer.getState() != ParticipantState.WAITING) {
				// Destinataire déjà IN_CALL, ou appels croisés simultanés (il sonne déjà) : BUSY.
				ObjectNode busy = codec.object();
				busy.put("calleeId", peer.getUserId());
				busy.put("calleeState", peer.getState().name());
				sender.send(codec.message(MessageType.BUSY, room.consultationId, SignalMessage.SERVER_SENDER, busy));
				return;
			}
			room.callId = UUID.randomUUID().toString();
			room.callerRole = sender.getRole();
			room.callStartedAt = 0;
			room.callerId = sender.getUserId();
			room.calleeId = peer.getUserId();
			sender.setState(ParticipantState.RINGING);
			peer.setState(ParticipantState.RINGING);
			forward(room, sender, peer, type, payload);
			return;

		case CALL_ACCEPT:
			if (!room.isRinging() || isCaller || peer == null) {
				sendError(sender, type, "INVALID_STATE", "No incoming call to accept");
				return;
			}
			room.callStartedAt = System.currentTimeMillis(); // début effectif (audit)
			sender.setState(ParticipantState.IN_CALL);
			peer.setState(ParticipantState.IN_CALL);
			CallLifecycle.scheduleAuditStart(room, audit, afterUnlock);
			forward(room, sender, peer, type, payload);
			return;

		case CALL_REJECT:
			if (!room.isRinging() || isCaller) {
				sendError(sender, type, "INVALID_STATE", "No incoming call to reject");
				return;
			}
			room.resetCall();
			forward(room, sender, peer, type, payload);
			return;

		case CALL_CANCEL:
			if (!room.isRinging() || !isCaller) {
				sendError(sender, type, "INVALID_STATE", "No outgoing call to cancel");
				return;
			}
			room.resetCall();
			forward(room, sender, peer, type, payload);
			return;

		case HANGUP:
			if (!room.isInCall()) {
				sendError(sender, type, "INVALID_STATE", "No call in progress");
				return;
			}
			CallLifecycle.endCall(room, CallEndReason.HANGUP, audit, afterUnlock);
			forward(room, sender, peer, type, payload);
			return;

		case SDP_OFFER:
		case SDP_ANSWER:
		case ICE_CANDIDATE:
			// La négociation peut commencer dès la sonnerie (pré-négociation) ou en appel.
			if (room.callId == null || peer == null) {
				sendError(sender, type, "INVALID_STATE", "No call being set up");
				return;
			}
			forward(room, sender, peer, type, payload);
			return;

		case ICE_RESTART:
			if (!room.isInCall() || peer == null) {
				sendError(sender, type, "INVALID_STATE", "ICE restart requires an established call");
				return;
			}
			forward(room, sender, peer, type, payload);
			return;

		default:
			sendError(sender, type, "FORBIDDEN_TYPE", type + " cannot be sent by a client");
		}
	}

	private void forward(ConsultationRoom room, RoomParticipant sender, RoomParticipant peer, MessageType type,
			JsonNode payload) {
		if (peer != null) {
			peer.send(codec.message(type, room.consultationId, sender.getUserId(), payload));
		}
	}

	private void sendError(RoomParticipant to, MessageType rejected, String code, String message) {
		ObjectNode p = codec.object();
		p.put("code", code);
		p.put("message", message);
		if (rejected != null) {
			p.put("rejectedType", rejected.name());
		}
		to.send(codec.message(MessageType.ERROR, to.getIdentity().getConsultationId(), SignalMessage.SERVER_SENDER,
				p));
	}
}
