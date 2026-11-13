package com.teleconsult.service;

import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.teleconsult.model.CallAuditRecord;
import com.teleconsult.model.CallEndReason;
import com.teleconsult.service.repository.CallAuditRepository;

/**
 * Journal d'audit médical des communications. Les horodatages sont pris par le
 * serveur (jamais par le client) :
 * - début effectif = réception du CALL_ACCEPT ;
 * - fin = HANGUP, ou fermeture brutale de la WebSocket pendant l'état IN_CALL ;
 * - durée exacte = fin - début, en secondes avec 3 décimales (précision ms).
 *
 * Une panne de la base ne doit pas couper un appel en cours : l'erreur est
 * journalisée (SEVERE) avec toutes les données, pour reprise manuelle.
 */
@ApplicationScoped
public class CallAuditService {

	private static final Logger LOG = Logger.getLogger(CallAuditService.class.getName());

	@Inject
	private CallAuditRepository repository;

	public void callStarted(String callId, int consultationId, String callerId, String calleeId, long startedAt) {
		CallAuditRecord r = new CallAuditRecord(callId, consultationId, callerId, calleeId, startedAt);
		try {
			repository.recordStart(r);
			LOG.info("AUDIT début appel " + callId + " consultation=" + consultationId + " " + callerId + " -> "
					+ calleeId);
		} catch (RuntimeException e) {
			LOG.log(Level.SEVERE, "AUDIT NON ENREGISTRÉ (début) call=" + callId + " consultation=" + consultationId
					+ " caller=" + callerId + " callee=" + calleeId + " startedAt=" + startedAt, e);
		}
	}

	public void callEnded(String callId, int consultationId, String callerId, String calleeId, long startedAt,
			long endedAt, CallEndReason reason) {
		CallAuditRecord r = new CallAuditRecord(callId, consultationId, callerId, calleeId, startedAt);
		r.setEndedAt(endedAt);
		r.setDurationSeconds(durationSeconds(startedAt, endedAt));
		r.setEndReason(reason);
		try {
			repository.recordEnd(r);
			LOG.info("AUDIT fin appel " + callId + " durée=" + r.getDurationSeconds() + "s motif=" + reason);
		} catch (RuntimeException e) {
			LOG.log(Level.SEVERE, "AUDIT NON ENREGISTRÉ (fin) call=" + callId + " consultation=" + consultationId
					+ " startedAt=" + startedAt + " endedAt=" + endedAt + " reason=" + reason, e);
		}
	}

	/** Durée exacte en secondes, à la milliseconde près (jamais négative). */
	static BigDecimal durationSeconds(long startedAt, long endedAt) {
		return BigDecimal.valueOf(Math.max(0L, endedAt - startedAt), 3);
	}
}
