package com.admin.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.dao.DoctorDAO;
import com.db.DBConnect;

@WebServlet("/deleteDoctor")
public class DeleteDoctor extends HttpServlet {

	@Override
	// POST uniquement (plus de GET) : une action destructrice ne doit pas pouvoir être
	// déclenchée par un simple lien ou une image ; le jeton CSRF est vérifié par CsrfFilter.
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		int id = Integer.parseInt(req.getParameter("id"));
		
		DoctorDAO dao = new DoctorDAO(DBConnect.getConn());
		HttpSession session = req.getSession();

		if (dao.deleteDoctor(id)) {
			session.setAttribute("succMsg", "Le médecin a été supprimé.");
			resp.sendRedirect("admin/view_doctor.jsp");
		} else {
			session.setAttribute("errorMsg", "Une erreur est survenue, veuillez réessayer.");
			resp.sendRedirect("admin/view_doctor.jsp");
		}
		

	}

}