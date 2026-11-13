<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, com.db.DBConnect, com.dao.AppointmentDAO, com.dao.DoctorDAO"%>
<%@ page import="com.entity.Appointment, com.entity.Doctor, com.entity.User, com.util.Html"%>
<c:if test="${empty userObj}">
	<c:redirect url="user_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Mes rendez-vous · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-patient">
	<c:set var="nav" value="appointments" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>
	<%
		User me = (User) session.getAttribute("userObj");
		List<Appointment> list = new AppointmentDAO(DBConnect.getConn()).getAllAppointmentByLoginUser(me.getId());
		DoctorDAO doctors = new DoctorDAO(DBConnect.getConn());
	%>
	<main class="app-main container">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/index.jsp">Mon espace</a> / Mes rendez-vous</div>
				<h1>Mes rendez-vous</h1>
				<p>Suivez vos demandes, rejoignez vos téléconsultations et consultez les comptes rendus.</p>
			</div>
			<a href="${ctx}/user_appointment.jsp" class="btn btn-primary"><i class="far fa-calendar-plus"></i> Nouveau rendez-vous</a>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<div class="card-soft table-wrap">
			<% if (list.isEmpty()) { %>
			<div class="empty-state">
				<i class="far fa-calendar"></i>
				<h3>Vous n'avez encore aucun rendez-vous</h3>
				<p>Prenez rendez-vous avec l'un de nos médecins en quelques clics.</p>
				<a href="${ctx}/user_appointment.jsp" class="btn btn-primary">Prendre rendez-vous</a>
			</div>
			<% } else { %>
			<div class="table-responsive">
				<table class="table table-modern">
					<thead>
						<tr><th>Date</th><th>Médecin</th><th>Motif</th><th>Statut / compte rendu</th><th class="text-end">Téléconsultation</th></tr>
					</thead>
					<tbody>
					<% for (Appointment ap : list) {
						Doctor d = doctors.getDoctorById(ap.getDoctorId());
						boolean pending = "Pending".equals(ap.getStatus()); %>
						<tr>
							<td class="cell-main nowrap"><%=Html.esc(ap.getAppoinDate())%>
								<span class="cell-sub"><%=Html.esc(ap.getFullName())%>, <%=Html.esc(ap.getAge())%> ans</span></td>
							<td><%=d == null ? "Médecin indisponible" : "Dr " + Html.esc(d.getFullName())%>
								<span class="cell-sub"><%=d == null ? "" : Html.esc(d.getSpecialist())%></span></td>
							<td><%=Html.esc(ap.getDiseases())%></td>
							<td><% if (pending) { %><span class="status status-pending">En attente</span>
								<% } else { %><span class="status status-done">Traité</span>
								<span class="status-note"><%=Html.esc(ap.getStatus())%></span><% } %></td>
							<td class="text-end">
								<% if (d != null) { %>
								<a class="btn btn-sm btn-tele" href="${ctx}/teleconsultation/consultation.jsp?id=<%=ap.getId()%>"><i class="fas fa-video"></i> Rejoindre</a>
								<% } %>
							</td>
						</tr>
					<% } %>
					</tbody>
				</table>
			</div>
			<% } %>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
