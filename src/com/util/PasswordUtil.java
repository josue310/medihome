package com.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.util.encoders.Base64;

/**
 * Hachage des mots de passe avec scrypt (implémentation Bouncy Castle).
 *
 * Format stocké en base (paramètres inclus, pour pouvoir les durcir plus tard
 * sans casser les anciens hachages) :
 *
 * <pre>
 * $scrypt$ln=14,r=8,p=1$&lt;sel base64&gt;$&lt;hachage base64&gt;
 * </pre>
 *
 * Paramètres : N = 2^14, r = 8, p = 1 (≈ 16 Mo de mémoire par calcul). C'est le
 * réglage « connexion interactive » recommandé par l'auteur de scrypt ; il reste
 * raisonnable pour le tas de 256 Mo configuré sur le Tomcat de ce projet.
 * Si le serveur dispose de plus de mémoire, augmenter LOG_N (ex. 17 selon OWASP).
 */
public final class PasswordUtil {

	private static final String PREFIX = "$scrypt$";
	private static final int LOG_N = 14;
	private static final int R = 8;
	private static final int P = 1;
	private static final int SALT_BYTES = 16;
	private static final int KEY_BYTES = 32;

	/** Bornes de sécurité lors de la lecture d'un hachage (évite un calcul démesuré). */
	private static final int MAX_LOG_N = 20;

	public static final int MIN_LENGTH = 8;
	public static final int MAX_LENGTH = 128;

	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * Hachage factice, vérifié quand l'email n'existe pas : la réponse prend alors
	 * le même temps, ce qui empêche de deviner les comptes existants au chronomètre.
	 */
	private static final String DUMMY_HASH = hash("dummy-password-for-timing");

	private PasswordUtil() {
	}

	/** Retourne le hachage scrypt (avec sel aléatoire) du mot de passe. */
	public static String hash(String password) {
		if (password == null) {
			throw new IllegalArgumentException("password is null");
		}
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		byte[] key = derive(password, salt, LOG_N, R, P, KEY_BYTES);
		return PREFIX + "ln=" + LOG_N + ",r=" + R + ",p=" + P + "$" + Base64.toBase64String(salt) + "$"
				+ Base64.toBase64String(key);
	}

	/**
	 * Vérifie un mot de passe contre un hachage stocké, en temps constant.
	 * Retourne false si le hachage est absent ou mal formé (jamais d'exception).
	 */
	public static boolean verify(String password, String stored) {
		if (password == null || stored == null || !stored.startsWith(PREFIX)) {
			return false;
		}
		try {
			// stored = "$scrypt$ln=14,r=8,p=1$salt$hash" -> ["", "scrypt", "ln=..", salt, hash]
			String[] parts = stored.split("\\$");
			if (parts.length != 5) {
				return false;
			}
			int logN = -1, r = -1, p = -1;
			for (String kv : parts[2].split(",")) {
				String[] pair = kv.split("=");
				if (pair.length != 2) {
					return false;
				}
				int v = Integer.parseInt(pair[1]);
				if ("ln".equals(pair[0])) {
					logN = v;
				} else if ("r".equals(pair[0])) {
					r = v;
				} else if ("p".equals(pair[0])) {
					p = v;
				}
			}
			if (logN < 1 || logN > MAX_LOG_N || r < 1 || r > 32 || p < 1 || p > 16) {
				return false;
			}
			byte[] salt = Base64.decode(parts[3]);
			byte[] expected = Base64.decode(parts[4]);
			byte[] actual = derive(password, salt, logN, r, p, expected.length);
			return MessageDigest.isEqual(expected, actual);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** À appeler quand le compte n'existe pas, pour égaliser le temps de réponse. */
	public static void verifyDummy(String password) {
		verify(password == null ? "" : password, DUMMY_HASH);
	}

	/** Vrai si la valeur stockée est déjà un hachage produit par cette classe. */
	public static boolean isHashed(String stored) {
		return stored != null && stored.startsWith(PREFIX);
	}

	/** Politique minimale : 8 à 128 caractères. */
	public static boolean isAcceptable(String password) {
		return password != null && password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
	}

	private static byte[] derive(String password, byte[] salt, int logN, int r, int p, int keyLen) {
		return SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, 1 << logN, r, p, keyLen);
	}

}
