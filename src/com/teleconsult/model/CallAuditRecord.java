package com.teleconsult.model;

import java.math.BigDecimal;

/** Entrée du journal d'audit médical d'un appel (prête à devenir une entité JPA). */
public class CallAuditRecord {

	private String callId;
	private int consultationId;
	private String callerId;
	private String calleeId;
	/** Début effectif de la communication (CALL_ACCEPT), en ms epoch. */
	private Long startedAt;
	/** Fin de la communication, en ms epoch. */
	private Long endedAt;
	/** Durée exacte en secondes, à la milliseconde près (ex. 125.347). */
	private BigDecimal durationSeconds;
	private CallEndReason endReason;

	public CallAuditRecord() {
	}

	public CallAuditRecord(String callId, int consultationId, String callerId, String calleeId, Long startedAt) {
		this.callId = callId;
		this.consultationId = consultationId;
		this.callerId = callerId;
		this.calleeId = calleeId;
		this.startedAt = startedAt;
	}

	public String getCallId() {
		return callId;
	}

	public void setCallId(String callId) {
		this.callId = callId;
	}

	public int getConsultationId() {
		return consultationId;
	}

	public void setConsultationId(int consultationId) {
		this.consultationId = consultationId;
	}

	public String getCallerId() {
		return callerId;
	}

	public void setCallerId(String callerId) {
		this.callerId = callerId;
	}

	public String getCalleeId() {
		return calleeId;
	}

	public void setCalleeId(String calleeId) {
		this.calleeId = calleeId;
	}

	public Long getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Long startedAt) {
		this.startedAt = startedAt;
	}

	public Long getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(Long endedAt) {
		this.endedAt = endedAt;
	}

	public BigDecimal getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(BigDecimal durationSeconds) {
		this.durationSeconds = durationSeconds;
	}

	public CallEndReason getEndReason() {
		return endReason;
	}

	public void setEndReason(CallEndReason endReason) {
		this.endReason = endReason;
	}
}
