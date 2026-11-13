package com.teleconsult.model;

/** Rôle d'un participant dans une consultation. */
public enum Role {
	MEDECIN, PATIENT;

	/** Rôle opposé (le correspondant dans un appel 1-à-1). */
	public Role other() {
		return this == MEDECIN ? PATIENT : MEDECIN;
	}

	/** Conversion tolérante (null si absent ou inconnu). */
	public static Role parse(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		try {
			return Role.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
