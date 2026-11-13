package com.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.db.DBConnect;

/**
 * Prépare la base au démarrage de l'application (utile sur un hébergeur, où la
 * base est vide au premier lancement) :
 *
 * 1. exécute /WEB-INF/sql/schema.sql (CREATE TABLE IF NOT EXISTS : les tables
 *    et données existantes ne sont jamais modifiées) ;
 * 2. si aucun administrateur n'existe et que ADMIN_EMAIL / ADMIN_PASSWORD sont
 *    définis (variables d'environnement ou propriétés système admin.email /
 *    admin.password), crée ce compte avec un mot de passe haché (scrypt).
 *
 * Désactivable avec DB_BOOTSTRAP=false. Une erreur est journalisée mais
 * n'empêche pas l'application de démarrer.
 */
@WebListener
public class DatabaseBootstrap implements ServletContextListener {

	private static final Logger LOG = Logger.getLogger(DatabaseBootstrap.class.getName());

	@Override
	public void contextInitialized(ServletContextEvent event) {
		if ("false".equalsIgnoreCase(DBConnect.setting("db.bootstrap", "true"))) {
			LOG.info("Initialisation de la base désactivée (DB_BOOTSTRAP=false)");
			return;
		}
		Connection c = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			c = DriverManager.getConnection(
					DBConnect.setting("hospital.db.url", "jdbc:mysql://localhost:3306/hospital_2?useSSL=false"),
					DBConnect.setting("hospital.db.user", "root"), DBConnect.setting("hospital.db.password", ""));
			int n = runSchema(c, event.getServletContext().getResourceAsStream("/WEB-INF/sql/schema.sql"));
			LOG.info("Schéma de base vérifié (" + n + " instructions)");
			createAdminIfMissing(c);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Initialisation de la base impossible : vérifiez HOSPITAL_DB_URL / USER / PASSWORD", e);
		} finally {
			if (c != null) {
				try {
					c.close();
				} catch (SQLException ignored) {
					// fermeture best-effort
				}
			}
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		// rien à libérer
	}

	static int runSchema(Connection c, InputStream in) throws IOException, SQLException {
		if (in == null) {
			throw new IOException("/WEB-INF/sql/schema.sql introuvable");
		}
		List<String> statements = new ArrayList<String>();
		StringBuilder current = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("--")) {
					continue;
				}
				current.append(line).append('\n');
				if (trimmed.endsWith(";")) {
					statements.add(current.toString().trim().replaceAll(";$", ""));
					current.setLength(0);
				}
			}
		} finally {
			reader.close();
		}
		Statement st = c.createStatement();
		for (String sql : statements) {
			st.execute(sql);
		}
		st.close();
		return statements.size();
	}

	private static void createAdminIfMissing(Connection c) throws SQLException {
		String email = DBConnect.setting("admin.email", null);
		String password = DBConnect.setting("admin.password", null);
		Statement st = c.createStatement();
		ResultSet rs = st.executeQuery("select count(*) from admin");
		rs.next();
		boolean exists = rs.getInt(1) > 0;
		st.close();
		if (exists) {
			return;
		}
		if (email == null || password == null) {
			LOG.warning("Aucun administrateur : définissez ADMIN_EMAIL et ADMIN_PASSWORD puis redémarrez.");
			return;
		}
		if (!PasswordUtil.isAcceptable(password)) {
			LOG.warning("ADMIN_PASSWORD doit contenir entre 8 et 128 caractères : administrateur non créé.");
			return;
		}
		PreparedStatement ps = c.prepareStatement("insert into admin(email, password) values (?, ?)");
		ps.setString(1, email);
		ps.setString(2, PasswordUtil.hash(password));
		ps.executeUpdate();
		ps.close();
		LOG.info("Administrateur créé : " + email);
	}
}
