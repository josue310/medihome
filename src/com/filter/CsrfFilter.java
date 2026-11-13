package com.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.util.CsrfUtil;

/**
 * Protection CSRF globale :
 * - toute requête POST doit porter le jeton de la session (champ csrfToken),
 *   sinon elle est refusée (403) ;
 * - à l'affichage d'une page JSP, le jeton est créé s'il n'existe pas encore,
 *   pour que les formulaires puissent l'inclure (${sessionScope.csrfToken}).
 * Ajoute aussi des en-têtes de sécurité (anti-clickjacking, anti-sniffing).
 */
@WebFilter("/*")
public class CsrfFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;

		// Toutes les pages sont en UTF-8 : les formulaires envoient de l'UTF-8.
		// À fixer AVANT la première lecture d'un paramètre (ci-dessous pour le jeton).
		if (req.getCharacterEncoding() == null) {
			req.setCharacterEncoding("UTF-8");
		}

		// La page ne peut pas être affichée dans une iframe d'un autre site
		resp.setHeader("X-Frame-Options", "DENY");
		resp.setHeader("X-Content-Type-Options", "nosniff");
		resp.setHeader("Referrer-Policy", "same-origin");

		if ("POST".equalsIgnoreCase(req.getMethod())) {
			// Formulaires HTML : champ caché csrfToken.
			// API (JSON, multipart) : en-tête X-CSRF-Token, lu en premier pour ne pas
			// déclencher l'analyse du corps de la requête.
			String token = req.getHeader(CsrfUtil.HEADER);
			if (token == null) {
				token = req.getParameter(CsrfUtil.PARAM);
			}
			if (!CsrfUtil.isValid(req.getSession(false), token)) {
				resp.sendError(HttpServletResponse.SC_FORBIDDEN,
						"Invalid or expired form. Please go back, reload the page and try again.");
				return;
			}
		} else {
			String path = req.getRequestURI().substring(req.getContextPath().length());
			if (path.endsWith(".jsp") || path.endsWith("/")) {
				CsrfUtil.getToken(req.getSession());
			}
		}

		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}

}
