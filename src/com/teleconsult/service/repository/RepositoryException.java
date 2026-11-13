package com.teleconsult.service.repository;

/**
 * Erreur de persistance non vérifiée (même philosophie que
 * javax.persistence.PersistenceException) : les services ne dépendent ni de
 * JDBC ni de JPA.
 */
public class RepositoryException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RepositoryException(String message, Throwable cause) {
		super(message, cause);
	}
}
