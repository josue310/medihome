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
import javax.servlet.http.HttpSession;

/**
 * Contrôle d'accès centralisé : chaque URL protégée exige qu'un rôle précis
 * soit connecté (attribut de session posé par le servlet de login).
 * Sans ce filtre, les servlets d'action (ajout/suppression de médecin, etc.)
 * pouvaient être appelés directement sans être authentifié.
 */
@WebFilter(urlPatterns = {
		// Espace administrateur
		"/admin/*", "/addDoctor", "/updateDoctor", "/deleteDoctor", "/addSpecialist",
		// Espace médecin
		"/doctor/*", "/updateStatus", "/doctChangePassword", "/doctorUpdateProfile",
		// Espace patient
		"/appAppointment", "/userChangePassword", "/user_appointment.jsp", "/change_password.jsp",
		"/view_appointment.jsp" })
public class AuthFilter implements Filter {

	private static final String[] ADMIN_PATHS = { "/admin/", "/addDoctor", "/updateDoctor", "/deleteDoctor",
			"/addSpecialist" };

	private static final String[] DOCTOR_PATHS = { "/doctor/", "/updateStatus", "/doctChangePassword",
			"/doctorUpdateProfile" };

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;

		// Chemin relatif à l'application, ex. "/admin/doctor.jsp" ou "/deleteDoctor"
		String path = req.getRequestURI().substring(req.getContextPath().length());

		String requiredAttr;
		String loginPage;
		if (matches(path, ADMIN_PATHS)) {
			requiredAttr = "adminObj";
			loginPage = "/admin_login.jsp";
		} else if (matches(path, DOCTOR_PATHS)) {
			requiredAttr = "doctObj";
			loginPage = "/doctor_login.jsp";
		} else {
			requiredAttr = "userObj";
			loginPage = "/user_login.jsp";
		}

		// getSession(false) : ne crée pas de session pour un visiteur anonyme
		HttpSession session = req.getSession(false);
		if (session != null && session.getAttribute(requiredAttr) != null) {
			chain.doFilter(request, response);
			return;
		}

		req.getSession().setAttribute("errorMsg", "Veuillez vous connecter pour accéder à cette page.");
		resp.sendRedirect(req.getContextPath() + loginPage);
	}

	private static boolean matches(String path, String[] prefixes) {
		for (String p : prefixes) {
			if (p.endsWith("/") ? path.startsWith(p) : path.equals(p)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void destroy() {
	}

}
