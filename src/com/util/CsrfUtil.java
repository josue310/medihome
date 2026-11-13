package com.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.servlet.http.HttpSession;

import org.bouncycastle.util.encoders.Hex;

/**
 * Jeton anti-CSRF (« synchronizer token ») : un secret aléatoire par session,
 * recopié dans chaque formulaire POST (champ caché csrfToken) et vérifié par
 * CsrfFilter. Un site tiers ne peut pas le connaître, donc ne peut pas forger
 * de requête valide au nom de l'utilisateur.
 */
public final class CsrfUtil {

	public static final String SESSION_ATTR = "csrfToken";
	public static final String PARAM = "csrfToken";
	/** En-tête équivalent pour les appels fetch/XHR (API REST JSON et multipart). */
	public static final String HEADER = "X-CSRF-Token";

	private static final SecureRandom RANDOM = new SecureRandom();

	private CsrfUtil() {
	}

	public static String getToken(HttpSession session) {
		synchronized (session) {
			String token = (String) session.getAttribute(SESSION_ATTR);
			if (token == null) {
				byte[] bytes = new byte[32];
				RANDOM.nextBytes(bytes);
				token = Hex.toHexString(bytes);
				session.setAttribute(SESSION_ATTR, token);
			}
			return token;
		}
	}

	/** Comparaison en temps constant du jeton reçu avec celui de la session. */
	public static boolean isValid(HttpSession session, String received) {
		if (session == null || received == null) {
			return false;
		}
		String expected = (String) session.getAttribute(SESSION_ATTR);
		if (expected == null) {
			return false;
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				received.getBytes(StandardCharsets.UTF_8));
	}

}
