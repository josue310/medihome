package com.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class SessionUtil {

	private SessionUtil() {
	}

	/**
	 * Protection contre la fixation de session : à la connexion, l'ancienne
	 * session est détruite et une nouvelle (nouvel identifiant) est créée.
	 * (Tomcat 7 / Servlet 3.0 n'a pas encore request.changeSessionId().)
	 */
	public static HttpSession renew(HttpServletRequest req) {
		HttpSession old = req.getSession(false);
		if (old != null) {
			old.invalidate();
		}
		HttpSession session = req.getSession(true);
		CsrfUtil.getToken(session); // nouveau jeton CSRF pour la nouvelle session
		return session;
	}

}
