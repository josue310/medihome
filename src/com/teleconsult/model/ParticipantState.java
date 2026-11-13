package com.teleconsult.model;

/** État d'un participant connecté à la salle d'une consultation. */
public enum ParticipantState {
	/** Connecté, en salle d'attente, aucun appel en cours. */
	WAITING,
	/** Un appel est en train de sonner (côté appelant comme côté appelé). */
	RINGING,
	/** En communication. */
	IN_CALL
}
