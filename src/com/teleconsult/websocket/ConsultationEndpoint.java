package com.teleconsult.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.teleconsult.model.AuthenticatedParticipant;

/**
 * Endpoint de signalisation WebRTC : ws(s)://hote/contexte/ws/consultation/{consultationId}
 *
 * Aucun flux audio/vidéo ne transite ici : uniquement les messages JSON de
 * signalisation (SDP, candidats ICE, cycle d'appel) et des notifications.
 * Une instance par connexion (créée par ConsultationHandshakeConfigurator).
 */
@ServerEndpoint(value = "/ws/consultation/{consultationId}", configurator = ConsultationHandshakeConfigurator.class)
public class ConsultationEndpoint {

	private static final Logger LOG = Logger.getLogger(ConsultationEndpoint.class.getName());

	/** Délai max. d'un envoi bloquant vers un client lent (propriété Tomcat), en ms. */
	private static final String TOMCAT_BLOCKING_SEND_TIMEOUT = "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";

	private final ConsultationRoomManager roomManager;
	private final SignalingRouter router;

	/** Écrit dans @OnOpen, lu par les autres callbacks (threads du conteneur). */
	private volatile RoomParticipant participant;

	public ConsultationEndpoint(ConsultationRoomManager roomManager, SignalingRouter router) {
		this.roomManager = roomManager;
		this.router = router;
	}

	@OnOpen
	public void onOpen(Session session, EndpointConfig config) {
		Map<String, Object> props = config.getUserProperties();
		AuthenticatedParticipant identity = (AuthenticatedParticipant) props
				.get(ConsultationHandshakeConfigurator.KEY_PARTICIPANT);
		if (identity == null) {
			Object reason = props.get(ConsultationHandshakeConfigurator.KEY_REJECTION);
			closeQuietly(session, new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,
					reason == null ? "NOT_AUTHORIZED" : reason.toString()));
			return;
		}
		session.setMaxTextMessageBufferSize(SignalingRouter.MAX_MESSAGE_CHARS); // au-delà : fermeture 1009
		session.setMaxIdleTimeout(0); // la vivacité est contrôlée par le heartbeat (ping/pong)
		session.getUserProperties().put(TOMCAT_BLOCKING_SEND_TIMEOUT, Long.valueOf(5000));

		HttpSession httpSession = (HttpSession) props.get(ConsultationHandshakeConfigurator.KEY_HTTP_SESSION);
		participant = roomManager.join(identity, session, httpSession);
	}

	@OnMessage
	public void onMessage(String text) {
		RoomParticipant p = participant;
		if (p != null) {
			router.route(p, text);
		}
	}

	@OnMessage
	public void onPong(PongMessage pong) {
		RoomParticipant p = participant;
		if (p != null) {
			p.markAlive();
		}
	}

	@OnClose
	public void onClose(Session session, CloseReason reason) {
		RoomParticipant p = participant;
		participant = null;
		if (p != null) {
			roomManager.leave(p, reason);
		}
	}

	@OnError
	public void onError(Session session, Throwable error) {
		if (error instanceof IOException) {
			LOG.log(Level.FINE, "Erreur réseau WebSocket (client déconnecté)", error);
		} else {
			LOG.log(Level.WARNING, "Erreur WebSocket pour " + participant, error);
		}
		// Tomcat appelle ensuite @OnClose, qui retire le participant de la salle.
	}

	private static void closeQuietly(Session session, CloseReason reason) {
		try {
			session.close(reason);
		} catch (IOException e) {
			LOG.log(Level.FINE, "Close failed", e);
		}
	}
}
