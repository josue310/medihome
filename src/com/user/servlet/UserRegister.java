package com.user.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.dao.UserDAO;
import com.db.DBConnect;
import com.entity.User;
import com.util.PasswordUtil;

@WebServlet("/user_register")
public class UserRegister extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		try {
			String fullName = req.getParameter("fullname");
			String email = req.getParameter("email");
			String password = req.getParameter("password");

			if (!PasswordUtil.isAcceptable(password)) {
				req.getSession().setAttribute("errorMsg", "Le mot de passe doit contenir entre 8 et 128 caractères.");
				resp.sendRedirect("signup.jsp");
				return;
			}

			User u = new User(fullName, email, password);

			UserDAO dao = new UserDAO(DBConnect.getConn());

			HttpSession session = req.getSession();

			boolean f = dao.register(u);

			if (f) {

				session.setAttribute("succMsg", "Votre compte a été créé. Vous pouvez maintenant vous connecter.");
				resp.sendRedirect("user_login.jsp");

			} else {
				session.setAttribute("errorMsg", "Une erreur est survenue, veuillez réessayer.");
				resp.sendRedirect("signup.jsp");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}