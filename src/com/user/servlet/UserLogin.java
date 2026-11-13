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
import com.util.SessionUtil;

@WebServlet("/userLogin")
public class UserLogin extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String email = req.getParameter("email");
		String password = req.getParameter("password");

		UserDAO dao = new UserDAO(DBConnect.getConn());
		User user = dao.login(email, password);

		if (user != null) {
			HttpSession session = SessionUtil.renew(req); // anti fixation de session
			session.setAttribute("userObj", user);
			resp.sendRedirect("index.jsp");
		} else {
			req.getSession().setAttribute("errorMsg", "Email ou mot de passe incorrect.");
			resp.sendRedirect("user_login.jsp");
		}

	}

}