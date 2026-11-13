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

@WebServlet("/userChangePassword")
public class ChangePassword extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		// Identité prise dans la session (posée au login), jamais dans la requête
		int uid = ((User) req.getSession().getAttribute("userObj")).getId();
		String oldPassword = req.getParameter("oldPassword");
		String newPassword = req.getParameter("newPassword");

		UserDAO dao = new UserDAO(DBConnect.getConn());
		HttpSession session = req.getSession();

		if (!PasswordUtil.isAcceptable(newPassword)) {
			session.setAttribute("errorMsg", "Le mot de passe doit contenir entre 8 et 128 caractères.");
			resp.sendRedirect("change_password.jsp");
			return;
		}

		if (dao.checkOldPassword(uid, oldPassword)) {

			if (dao.changePassword(uid, newPassword)) {
				session.setAttribute("succMsg", "Votre mot de passe a été modifié.");
				resp.sendRedirect("change_password.jsp");

			} else {
				session.setAttribute("errorMsg", "Une erreur est survenue, veuillez réessayer.");
				resp.sendRedirect("change_password.jsp");
			}

		} else {
			session.setAttribute("errorMsg", "L'ancien mot de passe est incorrect.");
			resp.sendRedirect("change_password.jsp");
		}

	}

}