package com.teleconsult.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.Session;

import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.model.ParticipantState;
import com.teleconsult.model.Role;

/**
 * Une connexion WebSocket active dans une salle.
 *
 * Thread-safety :
 * - l'état (state) n'est modifié que sous le verrou de la salle (synchronized(room)) ;
 * - send() / ping() sont synchronisés sur le participant : RemoteEndpoint.Basic
 *   n'accepte pas d'envois concurrents (sinon IllegalStateException
 *   « TEXT_FULL_WRITING »). Ordre des verrous toujours salle -> participant,
 *   jamais l'inverse : pas d'interblocage.
 */
public final class RoomParticipant {

	private static final Logger LOG = Logger.getLogger(RoomParticipant.class.getName());

	private final AuthenticatedParticipant identity;
	private final Session session;
	private final HttpSession httpSession;
	private final ConsultationRoom room;

	private volatile ParticipantState state = ParticipantState.WAITING;
	private volatile long lastSeen = System.currentTimeMillis();

	RoomParticipant(AuthenticatedParticipant identity, Session session, HttpSession httpSession,
			ConsultationRoom room) {
		this.identity = identity;
		this.session = session;
		this.httpSession = httpSession;
		this.room = room;
	}

	public AuthenticatedParticipant getIdentity() {
		return identity;
	}

	public String getUserId() {
		return identity.getUserId();
	}

	public Role getRole() {
		return identity.getRole();
	}

	HttpSession getHttpSession() {
		return httpSession;
	}

	ConsultationRoom getRoom() {
		return room;
	}

	public ParticipantState getState() {
		return state;
	}

	void setState(ParticipantState state) {
		this.state = state;
	}

	long getLastSeen() {
		return lastSeen;
	}

	/** Appelé à chaque message ou pong reçu : la connexion est vivante. */
	void markAlive() {
		lastSeen = System.currentTimeMillis();
	}

	/**
	 * Envoi bloquant (avec délai max. configuré sur la session). En cas d'échec,
	 * on ne ferme pas ici (on peut être sous le verrou de la salle) : le
	 * heartbeat fermera la connexion morte.
	 */
	synchronized boolean send(String json) {
		if (!session.isOpen()) {
			return false;
		}
		try {
			session.getBasicRemote().sendText(json);
			return true;
		} catch (Exception e) {
			LOG.log(Level.FINE, "Send failed to " + identity, e);
			return false;
		}
	}

	synchronized void ping() {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.getBasicRemote().sendPing(ByteBuffer.wrap(new byte[] { 0x48, 0x42 }));
		} catch (Exception e) {
			LOG.log(Level.FINE, "Ping failed to " + identity, e);
		}
	}

	/** Fermeture silencieuse. Ne PAS appeler en détenant le verrou de la salle (@OnClose rappelle leave()). */
	void close(CloseReason.CloseCode code, String reason) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.close(new CloseReason(code, reason));
		} catch (Exception e) {
			LOG.log(Level.FINE, "Close failed for " + identity, e);
		}
	}

	@Override
	public String toString() {
		return identity + "[" + state + "]";
	}
}
