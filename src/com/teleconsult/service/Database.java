package com.teleconsult.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Fabrique de connexions JDBC. Chaque appel ouvre une connexion que l'appelant
 * DOIT fermer (try-with-resources). Pour la production, remplacer par un pool
 * (DataSource Tomcat JDBC déclarée dans context.xml).
 */
@ApplicationScoped
public class Database {

	@Inject
	private TeleconsultationConfig config;

	@PostConstruct
	void init() {
		try {
			// Indispensable dans Tomcat : le driver est dans WEB-INF/lib, invisible
			// du chargement automatique JDBC 4 effectué par le classloader système.
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Driver MySQL introuvable dans WEB-INF/lib", e);
		}
	}

	public Connection getConnection() throws SQLException {
		String url = config.getDbUrl();
		if (!url.contains("characterEncoding")) {
			// stockage correct des accents/emojis du chat
			url += (url.contains("?") ? "&" : "?") + "useUnicode=true&characterEncoding=UTF-8";
		}
		return DriverManager.getConnection(url, config.getDbUser(), config.getDbPassword());
	}
}
