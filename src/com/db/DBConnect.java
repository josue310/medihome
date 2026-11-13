package com.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connexion JDBC des DAO historiques.
 *
 * Configuration (même clés que le module téléconsultation), par ordre de priorité :
 * propriété système JVM puis variable d'environnement :
 *   hospital.db.url      / HOSPITAL_DB_URL       (défaut : base locale Laragon)
 *   hospital.db.user     / HOSPITAL_DB_USER      (défaut : root)
 *   hospital.db.password / HOSPITAL_DB_PASSWORD  (défaut : vide)
 *
 * Les DAO historiques ne ferment jamais leurs connexions : ouvrir une connexion
 * à chaque appel épuisait le serveur MySQL (fuite). Une connexion unique,
 * partagée et revalidée, est donc réutilisée (le driver MySQL synchronise
 * l'accès concurrent) ; elle est recréée automatiquement si elle est fermée
 * ou expirée (wait_timeout, redémarrage de la base...).
 */
public class DBConnect {

	private static final Logger LOG = Logger.getLogger(DBConnect.class.getName());
	private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/hospital_2?useSSL=false";

	private static Connection conn;

	public static synchronized Connection getConn() {
		try {
			if (conn == null || conn.isClosed() || !conn.isValid(3)) {
				if (conn != null) {
					try {
						conn.close();
					} catch (SQLException ignored) {
						// connexion déjà inutilisable
					}
				}
				Class.forName("com.mysql.jdbc.Driver");
				conn = DriverManager.getConnection(setting("hospital.db.url", DEFAULT_URL),
						setting("hospital.db.user", "root"), setting("hospital.db.password", ""));
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Connexion à la base de données impossible", e);
			conn = null;
		}
		return conn;
	}

	/** Propriété système, sinon variable d'environnement (hospital.db.url -> HOSPITAL_DB_URL). */
	public static String setting(String key, String defaultValue) {
		String v = System.getProperty(key);
		if (v == null || v.trim().isEmpty()) {
			v = System.getenv(key.toUpperCase().replace('.', '_'));
		}
		return v == null || v.trim().isEmpty() ? defaultValue : v.trim();
	}
}
