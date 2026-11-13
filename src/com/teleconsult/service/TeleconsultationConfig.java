package com.teleconsult.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

/**
 * Configuration du module. Ordre de priorité pour chaque clé (ex. coturn.secret) :
 * 1. propriété système JVM  (-Dcoturn.secret=...)
 * 2. variable d'environnement (COTURN_SECRET)
 * 3. fichier teleconsultation.properties (WEB-INF/classes, non servi au public)
 * 4. valeur par défaut
 * Les secrets (coturn.secret) devraient être fournis par 1 ou 2, pas par le fichier.
 */
@ApplicationScoped
public class TeleconsultationConfig {

	private static final Logger LOG = Logger.getLogger(TeleconsultationConfig.class.getName());

	public static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/hospital_2?useSSL=false";

	private final Properties file = new Properties();

	@PostConstruct
	void load() {
		InputStream in = TeleconsultationConfig.class.getResourceAsStream("/teleconsultation.properties");
		try {
			if (in != null) {
				file.load(in);
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Lecture de teleconsultation.properties impossible", e);
		} finally {
			closeQuietly(in);
		}
		if (getCoturnSecret().isEmpty() && getTurnUsername().isEmpty() && !getTurnUrls().isEmpty()) {
			LOG.warning("turn.urls est configuré sans coturn.secret ni turn.username : seuls les serveurs STUN seront fournis.");
		}
	}

	static void closeQuietly(java.io.Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException ignored) {
				// fermeture best-effort
			}
		}
	}

	public String get(String key, String defaultValue) {
		String v = System.getProperty(key);
		if (v == null) {
			v = System.getenv(key.toUpperCase().replace('.', '_'));
		}
		if (v == null) {
			v = file.getProperty(key);
		}
		return v == null ? defaultValue : v.trim();
	}

	public long getLong(String key, long defaultValue) {
		try {
			return Long.parseLong(get(key, String.valueOf(defaultValue)));
		} catch (NumberFormatException e) {
			LOG.warning("Valeur numérique invalide pour " + key + ", défaut utilisé : " + defaultValue);
			return defaultValue;
		}
	}

	private List<String> getList(String key) {
		List<String> out = new ArrayList<String>();
		for (String part : get(key, "").split(",")) {
			if (!part.trim().isEmpty()) {
				out.add(part.trim());
			}
		}
		return Collections.unmodifiableList(out);
	}

	// --- Base de données (même propriété que com.db.DBConnect) ---
	public String getDbUrl() {
		return get("hospital.db.url", DEFAULT_DB_URL);
	}

	public String getDbUser() {
		return get("hospital.db.user", "root");
	}

	public String getDbPassword() {
		return get("hospital.db.password", "");
	}

	// --- WebRTC / Coturn ---
	public List<String> getStunUrls() {
		return getList("stun.urls");
	}

	public List<String> getTurnUrls() {
		return getList("turn.urls");
	}

	/** Secret partagé avec Coturn (option static-auth-secret / use-auth-secret). */
	public String getCoturnSecret() {
		return get("coturn.secret", "");
	}

	/** Identifiants fixes d'un service TURN tiers (si pas de coturn.secret). */
	public String getTurnUsername() {
		return get("turn.username", "");
	}

	public String getTurnCredential() {
		return get("turn.credential", "");
	}

	/** Durée de validité des identifiants TURN : 2 heures par défaut. */
	public long getTurnCredentialTtlSeconds() {
		return getLong("turn.credential.ttl.seconds", 7200);
	}

	// --- Pièces jointes ---
	/** Dossier de stockage, HORS de l'application web (jamais servi directement). */
	public File getUploadDirectory() {
		return new File(get("upload.dir",
				System.getProperty("user.home") + File.separator + "gestionhopital-uploads"));
	}

	/** Taille maximale d'un fichier : 10 Mo. */
	public long getMaxUploadBytes() {
		return getLong("upload.max.bytes", 10L * 1024 * 1024);
	}

	// --- Chat ---
	public int getMaxChatMessageLength() {
		return (int) getLong("chat.max.length", 2000);
	}
}
