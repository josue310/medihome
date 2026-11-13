<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, java.util.ArrayList, java.util.HashSet, java.util.Set"%>
<%@ page import="com.db.DBConnect, com.dao.AppointmentDAO, com.entity.Appointment, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty doctObj}">
	<c:redirect url="../doctor_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Tableau de bord · Espace médecin</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-doctor">
	<c:set var="nav" value="dashboard" />
	<%@include file="/WEB-INF/jspf/navbar-doctor.jspf"%>
	<%
		Doctor me = (Doctor) session.getAttribute("doctObj");
		List<Appointment> all = new AppointmentDAO(DBConnect.getConn()).getAllAppointmentByDoctorLogin(me.getId());
		int pending = 0;
		Set<Integer> patients = new HashSet<Integer>();
		List<Appointment> queue = new ArrayList<Appointment>();
		for (Appointment a : all) {
			patients.add(a.getUserId());
			if ("Pending".equals(a.getStatus())) {
				pending++;
				if (queue.size() < 6) {
					queue.add(a);
				}
			}
		}
	%>
	<main class="app-main container">
		<div class="card-soft welcome-banner mb-4 d-flex flex-wrap align-items-center justify-content-between gap-3">
			<div>
				<h1>Bonjour Dr ${fn:escapeXml(doctObj.fullName)}</h1>
				<p>${fn:escapeXml(doctObj.specialist)} · <%=pending%> consultation(s) en attente dans votre file.</p>
			</div>
			<a href="${ctx}/doctor/patient.jsp" class="btn btn-light btn-lg"><i class="fas fa-user-injured"></i> Voir mes patients</a>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>

		<div class="row g-3 mb-4">
			<div class="col-sm-6 col-lg-3"><div class="card-soft stat-card"><span class="stat-icon"><i class="far fa-calendar-alt"></i></span>
				<div><div class="stat-value"><%=all.size()%></div><div class="stat-label">Rendez-vous</div></div></div></div>
			<div class="col-sm-6 col-lg-3"><div class="card-soft stat-card"><span class="stat-icon tone-amber"><i class="far fa-hourglass"></i></span>
				<div><div class="stat-value"><%=pending%></div><div class="stat-label">En attente</div></div></div></div>
			<div class="col-sm-6 col-lg-3"><div class="card-soft stat-card"><span class="stat-icon tone-green"><i class="fas fa-file-medical"></i></span>
				<div><div class="stat-value"><%=all.size() - pending%></div><div class="stat-label">Comptes rendus rédigés</div></div></div></div>
			<div class="col-sm-6 col-lg-3"><div class="card-soft stat-card"><span class="stat-icon tone-violet"><i class="fas fa-users"></i></span>
				<div><div class="stat-value"><%=patients.size()%></div><div class="stat-label">Patients suivis</div></div></div></div>
		</div>

		<div class="row g-4">
			<div class="col-lg-8">
				<div class="card-soft">
					<div class="card-body">
						<div class="card-title-row">
							<h2>File d'attente</h2>
							<a href="${ctx}/doctor/patient.jsp" class="btn btn-sm btn-soft">Tous les patients</a>
						</div>
						<% if (queue.isEmpty()) { %>
						<div class="empty-state"><i class="fas fa-mug-hot"></i><h3>Aucune consultation en attente</h3><p>Les nouvelles demandes de vos patients apparaîtront ici.</p></div>
						<% } else { %>
						<div class="table-responsive">
							<table class="table table-modern">
								<thead><tr><th>Patient</th><th>Date</th><th>Motif</th><th class="text-end">Actions</th></tr></thead>
								<tbody>
								<% for (Appointment a : queue) { %>
									<tr>
										<td class="cell-main"><%=Html.esc(a.getFullName())%><span class="cell-sub"><%=Html.esc(a.getAge())%> ans</span></td>
										<td class="nowrap"><%=Html.esc(a.getAppoinDate())%></td>
										<td><%=Html.esc(a.getDiseases())%></td>
										<td><div class="table-actions">
											<a class="btn btn-sm btn-tele" href="${ctx}/teleconsultation/consultation.jsp?id=<%=a.getId()%>"><i class="fas fa-video"></i> Consulter</a>
											<a class="btn btn-sm btn-light-border" href="${ctx}/doctor/comment.jsp?id=<%=a.getId()%>"><i class="fas fa-pen"></i> Compte rendu</a>
										</div></td>
									</tr>
								<% } %>
								</tbody>
							</table>
						</div>
						<% } %>
					</div>
				</div>
			</div>
			<div class="col-lg-4">
				<div class="d-grid gap-3">
					<a class="card-soft action-tile" href="${ctx}/doctor/patient.jsp">
						<span class="stat-icon"><i class="fas fa-user-injured"></i></span>
						<span><strong>Mes patients</strong><small>Rendez-vous, téléconsultations</small></span>
					</a>
					<a class="card-soft action-tile" href="${ctx}/doctor/edit_profile.jsp">
						<span class="stat-icon tone-violet"><i class="fas fa-id-badge"></i></span>
						<span><strong>Mon profil</strong><small>Coordonnées et mot de passe</small></span>
					</a>
				</div>
			</div>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
