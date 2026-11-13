<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, com.db.DBConnect, com.dao.DoctorDAO, com.dao.SpecialistDAO, com.dao.AppointmentDAO"%>
<%@ page import="com.entity.Specalist, com.entity.Appointment, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty adminObj}">
	<c:redirect url="../admin_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Tableau de bord · Administration</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-admin">
	<c:set var="nav" value="dashboard" />
	<%@include file="/WEB-INF/jspf/navbar-admin.jspf"%>
	<%
		DoctorDAO dao = new DoctorDAO(DBConnect.getConn());
		List<Specalist> specs = new SpecialistDAO(DBConnect.getConn()).getAllSpecialist();
		List<Appointment> appts = new AppointmentDAO(DBConnect.getConn()).getAllAppointment();
		int pending = 0;
		for (Appointment a : appts) {
			if ("Pending".equals(a.getStatus())) {
				pending++;
			}
		}
	%>
	<main class="app-main container">
		<div class="page-header">
			<div>
				<h1>Tableau de bord</h1>
				<p>Vue d'ensemble de l'activité de l'établissement.</p>
			</div>
			<a href="${ctx}/admin/doctor.jsp" class="btn btn-primary"><i class="fas fa-user-plus"></i> Ajouter un médecin</a>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>

		<div class="row g-3 mb-4">
			<div class="col-sm-6 col-lg-3"><a class="card-soft stat-card text-decoration-none text-reset" href="${ctx}/admin/view_doctor.jsp"><span class="stat-icon"><i class="fas fa-user-md"></i></span>
				<div><div class="stat-value"><%=dao.countDoctor()%></div><div class="stat-label">Médecins</div></div></a></div>
			<div class="col-sm-6 col-lg-3"><div class="card-soft stat-card"><span class="stat-icon tone-green"><i class="fas fa-users"></i></span>
				<div><div class="stat-value"><%=dao.countUSer()%></div><div class="stat-label">Patients inscrits</div></div></div></div>
			<div class="col-sm-6 col-lg-3"><a class="card-soft stat-card text-decoration-none text-reset" href="${ctx}/admin/patient.jsp"><span class="stat-icon tone-amber"><i class="far fa-calendar-alt"></i></span>
				<div><div class="stat-value"><%=appts.size()%></div><div class="stat-label">Rendez-vous (<%=pending%> en attente)</div></div></a></div>
			<div class="col-sm-6 col-lg-3"><div class="card-soft stat-card"><span class="stat-icon tone-violet"><i class="fas fa-stethoscope"></i></span>
				<div><div class="stat-value"><%=specs.size()%></div><div class="stat-label">Spécialités</div></div></div></div>
		</div>

		<div class="row g-4">
			<div class="col-lg-8">
				<div class="card-soft table-wrap">
					<div class="card-body pb-0">
						<div class="card-title-row">
							<h2>Derniers rendez-vous</h2>
							<a href="${ctx}/admin/patient.jsp" class="btn btn-sm btn-soft">Tout voir</a>
						</div>
					</div>
					<% if (appts.isEmpty()) { %>
					<div class="empty-state"><i class="far fa-calendar"></i><h3>Aucun rendez-vous</h3></div>
					<% } else { %>
					<div class="table-responsive">
						<table class="table table-modern">
							<thead><tr><th>Patient</th><th>Médecin</th><th>Date</th><th>Statut</th></tr></thead>
							<tbody>
							<% for (int i = appts.size() - 1, n = 0; i >= 0 && n < 6; i--, n++) {
								Appointment a = appts.get(i);
								Doctor d = dao.getDoctorById(a.getDoctorId()); %>
								<tr>
									<td class="cell-main"><%=Html.esc(a.getFullName())%><span class="cell-sub"><%=Html.esc(a.getDiseases())%></span></td>
									<td><%=d == null ? "—" : "Dr " + Html.esc(d.getFullName())%></td>
									<td class="nowrap"><%=Html.esc(a.getAppoinDate())%></td>
									<td><%="Pending".equals(a.getStatus()) ? "<span class=\"status status-pending\">En attente</span>" : "<span class=\"status status-done\">Traité</span>"%></td>
								</tr>
							<% } %>
							</tbody>
						</table>
					</div>
					<% } %>
				</div>
			</div>
			<div class="col-lg-4">
				<div class="card-soft">
					<div class="card-body">
						<div class="card-title-row"><h2>Spécialités</h2></div>
						<form action="../addSpecialist" method="post" class="d-flex gap-2 mb-3">
							<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
							<label class="visually-hidden" for="specName">Nouvelle spécialité</label>
							<input id="specName" name="specName" type="text" class="form-control" placeholder="Ex. : Cardiologie" maxlength="60" required>
							<button type="submit" class="btn btn-primary" aria-label="Ajouter"><i class="fas fa-plus"></i></button>
						</form>
						<% if (specs.isEmpty()) { %>
						<p class="text-muted small mb-0">Aucune spécialité. Ajoutez-en une pour pouvoir créer des médecins.</p>
						<% } else { %>
						<div class="d-flex flex-wrap gap-2">
							<% for (Specalist s : specs) { %><span class="chip"><%=Html.esc(s.getSpecialistName())%></span><% } %>
						</div>
						<% } %>
					</div>
				</div>
			</div>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
