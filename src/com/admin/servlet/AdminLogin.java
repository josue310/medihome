package com.admin.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.dao.AdminDAO;
import com.db.DBConnect;
import com.entity.User;
import com.util.SessionUtil;

@WebServlet("/adminLogin")
public class AdminLogin extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {

			String email = req.getParameter("email");
			String password = req.getParameter("password");

			// Identifiants vérifiés en base (table admin, mot de passe haché scrypt)
			// et non plus codés en dur dans le code source
			AdminDAO dao = new AdminDAO(DBConnect.getConn());

			if (dao.login(email, password)) {
				HttpSession session = SessionUtil.renew(req); // anti fixation de session
				User admin = new User();
				admin.setEmail(email);
				session.setAttribute("adminObj", admin);
				resp.sendRedirect("admin/index.jsp");
			} else {
				req.getSession().setAttribute("errorMsg", "Email ou mot de passe incorrect.");
				resp.sendRedirect("admin_login.jsp");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}