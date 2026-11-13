package com.teleconsult.model;

/** Motif de fin d'une communication, enregistré dans le journal d'audit. */
public enum CallEndReason {
	/** Raccroché volontairement (message HANGUP). */
	HANGUP,
	/** Déconnexion brutale (@OnClose, perte réseau, heartbeat expiré) pendant l'appel. */
	CONNECTION_LOST,
	/** Le participant s'est reconnecté depuis un autre onglet/appareil pendant l'appel. */
	SESSION_REPLACED
}
