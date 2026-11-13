package com.teleconsult.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.event.Reception;
import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.Session;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teleconsult.dto.SignalMessage;
import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.model.CallEndReason;
import com.teleconsult.model.ChatNotificationEvent;
import com.teleconsult.model.MessageType;
import com.teleconsult.service.CallAuditService;
import com.teleconsult.model.ParticipantState;
import com.teleconsult.service.ConsultationSecurityService;
import com.teleconsult.service.TeleconsultationConfig;

/**
 * Registre thread-safe des salles de consultation (consultationId -> salle).
 *
 * - join / leave : arrivée et départ, avec notifications PARTICIPANT_JOINED /
 *   PARTICIPANT_LEFT à l'autre partie et état initial ROOM_STATE au nouvel arrivant ;
 * - une seule connexion par rôle : une reconnexion (nouvel onglet, rechargement)
 *   remplace l'ancienne, fermée avec le code 4001 ;
 * - heartbeat : ping toutes les 25 s ; connexion fermée si aucun signe de vie
 *   depuis 75 s ou si l'utilisateur s'est déconnecté de l'application ;
 * - relais des ChatNotificationEvent (CDI) en messages CHAT_NOTIFY.
 *
 * Règle de concurrence : les transitions se font sous synchronized(room) ; les
 * écritures d'audit (base de données) et les fermetures de sockets se font APRÈS
 * avoir relâché le verrou, pour ne jamais bloquer la salle sur une E/S lente.
 */
@ApplicationScoped
public class ConsultationRoomManager {

	private static final Logger LOG = Logger.getLogger(ConsultationRoomManager.class.getName());

	static final long HEARTBEAT_PERIOD_SECONDS = 25;
	static final long DEAD_AFTER_MILLIS = 75000;
	/** Code de fermeture applicatif (plage 4000-4999) : connexion remplacée. */
	static final int CLOSE_REPLACED = 4001;

	private final ConcurrentHashMap<Integer, ConsultationRoom> rooms = new ConcurrentHashMap<Integer, ConsultationRoom>();

	@Inject
	private SignalMessageCodec codec;

	@Inject
	private CallAuditService audit;

	@Inject
	private ConsultationSecurityService security;

	@Inject
	private TeleconsultationConfig config;

	private ScheduledExecutorService scheduler;

	@PostConstruct
	void start() {
		scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "teleconsultation-heartbeat");
				t.setDaemon(true);
				return t;
			}
		});
		scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				heartbeat();
			}
		}, HEARTBEAT_PERIOD_SECONDS, HEARTBEAT_PERIOD_SECONDS, TimeUnit.SECONDS);
	}

	@PreDestroy
	void stop() {
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
		for (ConsultationRoom room : rooms.values()) {
			List<RoomParticipant> all;
			synchronized (room) {
				all = room.snapshot();
			}
			for (RoomParticipant p : all) {
				p.close(CloseReason.CloseCodes.GOING_AWAY, "Server shutting down");
			}
		}
	}

	/** Ajoute une connexion authentifiée à la salle de sa consultation. */
	public RoomParticipant join(AuthenticatedParticipant identity, Session session, HttpSession httpSession) {
		int cid = identity.getConsultationId();
		List<Runnable> afterUnlock = new ArrayList<Runnable>();
		RoomParticipant joined;
		RoomParticipant replaced;
		while (true) {
			ConsultationRoom room = rooms.get(cid);
			if (room == null) {
				ConsultationRoom created = new ConsultationRoom(cid);
				room = rooms.putIfAbsent(cid, created);
				if (room == null) {
					room = created;
				}
			}
			synchronized (room) {
				if (room.closed) {
					continue; // salle retirée entre-temps : on en recrée une
				}
				joined = new RoomParticipant(identity, session, httpSession, room);
				replaced = room.put(joined);
				RoomParticipant peer = room.peerOf(joined);
				boolean resumed = false;
				if (room.isSuspended() && room.suspendedRole == joined.getRole()
						&& joined.getUserId().equals(room.suspendedUserId) && room.isInCall()) {
					// Retour dans le délai de grâce : l'appel continue (aucune fin d'audit).
					room.clearSuspension();
					joined.setState(ParticipantState.IN_CALL);
					resumed = true;
				}
				if (replaced != null) {
					// L'ancienne connexion du même rôle disparaît : appel éventuel terminé.
					boolean callEnded = CallLifecycle.endCall(room, CallEndReason.SESSION_REPLACED, audit, afterUnlock);
					if (peer != null) {
						peer.send(codec.message(MessageType.PARTICIPANT_LEFT, cid, SignalMessage.SERVER_SENDER,
								describe(replaced, "SESSION_REPLACED", callEnded)));
					}
				}
				joined.send(codec.message(MessageType.ROOM_STATE, cid, SignalMessage.SERVER_SENDER,
						roomState(room, joined)));
				if (peer != null) {
					ObjectNode info = describe(joined, null, false);
					info.put("resumed", resumed);
					peer.send(codec.message(MessageType.PARTICIPANT_JOINED, cid, SignalMessage.SERVER_SENDER, info));
				}
				break;
			}
		}
		if (replaced != null) {
			replaced.close(CloseReason.CloseCodes.getCloseCode(CLOSE_REPLACED), "Replaced by a newer connection");
		}
		runAll(afterUnlock);
		LOG.info("Rejoint : " + joined);
		return joined;
	}

	/**
	 * Retire une connexion (appelé par @OnClose). Sans effet si elle a déjà été remplacée.
	 *
	 * Coupure réseau (code 1006) pendant un appel établi : l'appel n'est PAS terminé
	 * tout de suite. Le participant est « suspendu » pendant le délai de grâce
	 * (ws.reconnect.grace.seconds, 20 s par défaut) ; s'il se reconnecte, l'appel
	 * reprend (le flux WebRTC pair-à-pair a souvent survécu) ; sinon l'appel est
	 * clos et l'audit retient l'heure réelle de la coupure.
	 */
	public void leave(RoomParticipant participant, CloseReason reason) {
		ConsultationRoom room = participant.getRoom();
		List<Runnable> afterUnlock = new ArrayList<Runnable>();
		int code = reason == null ? CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode() : reason.getCloseCode().getCode();
		synchronized (room) {
			if (room.get(participant.getRole()) != participant) {
				return; // connexion remplacée : son départ ne concerne plus la salle
			}
			room.remove(participant.getRole());
			RoomParticipant peer = room.get(participant.getRole().other());
			long graceMillis = config.getLong("ws.reconnect.grace.seconds", 20) * 1000L;

			if (code == CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode() && room.isInCall() && peer != null
					&& graceMillis > 0) {
				room.suspendedRole = participant.getRole();
				room.suspendedUserId = participant.getUserId();
				room.suspendedAt = System.currentTimeMillis();
				final long token = ++room.suspendToken;
				final ConsultationRoom r = room;
				ObjectNode info = describe(participant, "CONNECTION_INTERRUPTED", false);
				info.put("gracePeriodSeconds", graceMillis / 1000);
				peer.send(codec.message(MessageType.PARTICIPANT_LEFT, room.consultationId,
						SignalMessage.SERVER_SENDER, info));
				scheduler.schedule(new Runnable() {
					@Override
					public void run() {
						expireSuspension(r, token);
					}
				}, graceMillis, TimeUnit.MILLISECONDS);
			} else {
				// Si l'autre participant était lui-même suspendu, sa coupure marque la fin réelle.
				long endedAt = room.isSuspended() ? room.suspendedAt : System.currentTimeMillis();
				boolean callEnded = CallLifecycle.endCall(room, CallEndReason.CONNECTION_LOST, endedAt, audit,
						afterUnlock);
				if (peer != null) {
					peer.send(codec.message(MessageType.PARTICIPANT_LEFT, room.consultationId,
							SignalMessage.SERVER_SENDER, describe(participant, "DISCONNECTED", callEnded)));
				}
			}
			if (room.isEmpty()) {
				room.closed = true;
				rooms.remove(room.consultationId, room);
			}
		}
		runAll(afterUnlock);
		LOG.info("Quitté : " + participant + " (" + code + ")");
	}

	/** Fin du délai de grâce sans reconnexion : l'appel est clos (fin d'audit = heure de la coupure). */
	private void expireSuspension(ConsultationRoom room, long token) {
		List<Runnable> afterUnlock = new ArrayList<Runnable>();
		synchronized (room) {
			if (room.closed || !room.isSuspended() || room.suspendToken != token) {
				return; // reprise, raccroché ou salle fermée entre-temps
			}
			com.teleconsult.model.Role role = room.suspendedRole;
			String userId = room.suspendedUserId;
			boolean callEnded = CallLifecycle.endCall(room, CallEndReason.CONNECTION_LOST, room.suspendedAt, audit,
					afterUnlock);
			RoomParticipant peer = room.get(role.other());
			if (peer != null) {
				ObjectNode info = codec.object();
				info.put("userId", userId);
				info.put("role", role.name());
				info.put("reason", "DISCONNECTED");
				info.put("callEnded", callEnded);
				peer.send(codec.message(MessageType.PARTICIPANT_LEFT, room.consultationId,
						SignalMessage.SERVER_SENDER, info));
			}
		}
		runAll(afterUnlock);
		LOG.info("Délai de reconnexion expiré pour la consultation " + room.consultationId);
	}

	/** Relaye un nouveau message / document aux participants connectés (CHAT_NOTIFY). */
	public void onChatNotification(@Observes(notifyObserver = Reception.IF_EXISTS) ChatNotificationEvent event) {
		ConsultationRoom room = rooms.get(event.getConsultationId());
		if (room == null) {
			return; // personne de connecté : le message reste consultable en base
		}
		String json = codec.message(MessageType.CHAT_NOTIFY, event.getConsultationId(), event.getSenderId(),
				codec.toTree(event.getPayload()));
		List<RoomParticipant> targets;
		synchronized (room) {
			targets = room.snapshot();
		}
		for (RoomParticipant p : targets) {
			p.send(json);
		}
	}

	/** Nombre de salles actives (supervision). */
	public int getActiveRoomCount() {
		return rooms.size();
	}

	private ObjectNode describe(RoomParticipant p, String reason, boolean callEnded) {
		ObjectNode n = codec.object();
		n.put("userId", p.getUserId());
		n.put("role", p.getRole().name());
		n.put("displayName", p.getIdentity().getDisplayName());
		if (reason != null) {
			n.put("reason", reason);
			n.put("callEnded", callEnded);
		}
		return n;
	}

	private ObjectNode roomState(ConsultationRoom room, RoomParticipant self) {
		ObjectNode n = codec.object();
		ObjectNode me = describe(self, null, false);
		me.put("state", self.getState().name());
		n.set("self", me);
		RoomParticipant peer = room.peerOf(self);
		if (peer != null) {
			ObjectNode other = describe(peer, null, false);
			other.put("state", peer.getState().name());
			n.set("peer", other);
		} else {
			n.putNull("peer");
		}
		if (room.callId != null) {
			// Permet au client de reprendre un appel après reconnexion de sa WebSocket.
			ObjectNode call = codec.object();
			call.put("state", room.isInCall() ? "IN_CALL" : "RINGING");
			call.put("callerId", room.callerId);
			call.put("calleeId", room.calleeId);
			call.put("startedAt", room.callStartedAt);
			n.set("call", call);
		} else {
			n.putNull("call");
		}
		return n;
	}

	private void heartbeat() {
		try {
			long now = System.currentTimeMillis();
			List<RoomParticipant> all = new ArrayList<RoomParticipant>();
			for (ConsultationRoom room : rooms.values()) {
				synchronized (room) {
					all.addAll(room.snapshot());
				}
			}
			// Hors de tout verrou de salle : close() déclenche @OnClose -> leave().
			for (RoomParticipant p : all) {
				if (now - p.getLastSeen() > DEAD_AFTER_MILLIS) {
					LOG.info("Heartbeat expiré : " + p);
					p.close(CloseReason.CloseCodes.GOING_AWAY, "Heartbeat timeout");
				} else if (!security.isStillAuthenticated(p.getHttpSession(), p.getIdentity())) {
					LOG.info("Session HTTP terminée : " + p);
					p.close(CloseReason.CloseCodes.VIOLATED_POLICY, "Authentication session ended");
				} else {
					p.ping();
				}
			}
		} catch (Throwable t) {
			// une exception non rattrapée arrêterait définitivement la tâche planifiée
			LOG.log(Level.SEVERE, "Heartbeat failure", t);
		}
	}

	private static void runAll(List<Runnable> actions) {
		for (Runnable r : actions) {
			try {
				r.run();
			} catch (RuntimeException e) {
				LOG.log(Level.SEVERE, "Post-transition action failed", e);
			}
		}
	}

}
