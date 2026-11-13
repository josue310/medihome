package com.teleconsult.service.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fermeture silencieuse des connexions JDBC (syntaxe Java 6, sans try-with-resources).
 * Fermer une Connection ferme aussi ses Statement et ResultSet (spécification JDBC).
 */
final class Jdbc {

	private static final Logger LOG = Logger.getLogger(Jdbc.class.getName());

	private Jdbc() {
	}

	static void close(Connection c) {
		if (c == null) {
			return;
		}
		try {
			c.close();
		} catch (SQLException e) {
			LOG.log(Level.FINE, "Fermeture de connexion impossible", e);
		}
	}
}
