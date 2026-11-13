<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="com.db.DBConnect, com.dao.AppointmentDAO, com.entity.Appointment, com.entity.Doctor, com.util.Html"%>
<%
	// Contrôle d'accès : le rendez-vous doit exister et appartenir au médecin connecté.
	// Fait avant tout affichage pour que la redirection soit possible.
	Doctor currentDoctor = (Doctor) session.getAttribute("doctObj");
	if (currentDoctor == null) {
		response.sendRedirect("../doctor_login.jsp");
		return;
	}
	Appointment ap = null;
	try {
		ap = new AppointmentDAO(DBConnect.getConn()).getAppointmentById(Integer.parseInt(request.getParameter("id")));
	} catch (NumberFormatException e) {
		ap = null;
	}
	if (ap == null || ap.getDoctorId() != currentDoctor.getId()) {
		session.setAttribute("errorMsg", "Rendez-vous introuvable.");
		response.sendRedirect("patient.jsp");
		return;
	}
%>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Compte rendu · Espace médecin</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-doctor">
	<c:set var="nav" value="patients" />
	<%@include file="/WEB-INF/jspf/navbar-doctor.jspf"%>
	<main class="app-main container" style="max-width: 860px">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/doctor/patient.jsp">Mes patients</a> / Compte rendu</div>
				<h1>Compte rendu de consultation</h1>
				<p>Ce compte rendu sera visible par le patient dans son espace.</p>
			</div>
			<a class="btn btn-tele" href="${ctx}/teleconsultation/consultation.jsp?id=<%=ap.getId()%>"><i class="fas fa-video"></i> Ouvrir la téléconsultation</a>
		</div>
		<div class="row g-4">
			<div class="col-md-5">
				<div class="card-soft"><div class="card-body">
					<p class="form-section-title">Patient</p>
					<h2 class="h5 mb-1"><%=Html.esc(ap.getFullName())%></h2>
					<p class="text-muted mb-3"><%=Html.esc(ap.getAge())%> ans</p>
					<dl class="mb-0 small">
						<dt>Date du rendez-vous</dt><dd><%=Html.esc(ap.getAppoinDate())%></dd>
						<dt>Motif</dt><dd><%=Html.esc(ap.getDiseases())%></dd>
						<dt>Téléphone</dt><dd><%=Html.esc(ap.getPhNo())%></dd>
						<dt>E-mail</dt><dd class="mb-0"><%=Html.esc(ap.getEmail())%></dd>
					</dl>
				</div></div>
			</div>
			<div class="col-md-7">
				<form class="card-soft" action="../updateStatus" method="post">
					<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
					<input type="hidden" name="id" value="<%=ap.getId()%>">
					<div class="card-body">
						<label class="form-label" for="comm">Conclusions et recommandations</label>
						<textarea id="comm" name="comm" class="form-control mb-3" rows="8" maxlength="1000" required
							placeholder="Diagnostic, traitement prescrit, examens complémentaires, suivi…"></textarea>
						<div class="d-flex flex-wrap gap-2 justify-content-end">
							<a href="${ctx}/doctor/patient.jsp" class="btn btn-light-border">Annuler</a>
							<button type="submit" class="btn btn-primary"><i class="fas fa-check"></i> Enregistrer le compte rendu</button>
						</div>
					</div>
				</form>
			</div>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
