package com.teleconsult.websocket;

import java.util.List;

import com.teleconsult.model.CallEndReason;
import com.teleconsult.service.CallAuditService;

/**
 * Transitions de fin/début d'appel partagées par ConsultationRoomManager et
 * SignalingRouter. Méthodes à appeler SOUS le verrou de la salle ; les écritures
 * d'audit sont seulement planifiées (exécutées après le relâchement du verrou).
 */
final class CallLifecycle {

	private CallLifecycle() {
	}

	/** Termine l'appel avec l'heure courante comme heure de fin. */
	static boolean endCall(ConsultationRoom room, CallEndReason reason, CallAuditService audit,
			List<Runnable> afterUnlock) {
		return endCall(room, reason, System.currentTimeMillis(), audit, afterUnlock);
	}

	/**
	 * Termine l'appel en cours. Si la communication était établie, planifie
	 * l'enregistrement de sa fin (horodatage serveur fourni par l'appelant).
	 *
	 * @return true si un appel (sonnerie ou communication) était en cours
	 */
	static boolean endCall(ConsultationRoom room, CallEndReason reason, long endedAt, CallAuditService audit,
			List<Runnable> afterUnlock) {
		if (room.callId == null) {
			return false;
		}
		if (room.isInCall()) {
			final String callId = room.callId;
			final int cid = room.consultationId;
			final long startedAt = room.callStartedAt;
			final long end = endedAt;
			final String callerId = room.callerId;
			final String calleeId = room.calleeId;
			final CallEndReason why = reason;
			final CallAuditService auditService = audit;
			afterUnlock.add(new Runnable() {
				@Override
				public void run() {
					auditService.callEnded(callId, cid, callerId, calleeId, startedAt, end, why);
				}
			});
		}
		room.resetCall();
		return true;
	}

	/** Planifie l'enregistrement du début effectif (CALL_ACCEPT) de la communication. */
	static void scheduleAuditStart(ConsultationRoom room, final CallAuditService audit, List<Runnable> afterUnlock) {
		final String callId = room.callId;
		final int cid = room.consultationId;
		final long startedAt = room.callStartedAt;
		final String callerId = room.callerId;
		final String calleeId = room.calleeId;
		afterUnlock.add(new Runnable() {
			@Override
			public void run() {
				audit.callStarted(callId, cid, callerId, calleeId, startedAt);
			}
		});
	}
}
