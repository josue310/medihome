<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, com.db.DBConnect, com.dao.AppointmentDAO, com.dao.DoctorDAO, com.entity.Appointment, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty adminObj}">
	<c:redirect url="../admin_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Rendez-vous · Administration</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-admin">
	<c:set var="nav" value="appointments" />
	<%@include file="/WEB-INF/jspf/navbar-admin.jspf"%>
	<%
		List<Appointment> list = new AppointmentDAO(DBConnect.getConn()).getAllAppointment();
		DoctorDAO doctors = new DoctorDAO(DBConnect.getConn());
	%>
	<main class="app-main container">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/admin/index.jsp">Tableau de bord</a> / Rendez-vous</div>
				<h1>Rendez-vous</h1>
				<p><%=list.size()%> rendez-vous enregistrés dans l'établissement.</p>
			</div>
			<div class="search-box">
				<i class="fas fa-search"></i>
				<input type="search" class="form-control" placeholder="Patient, médecin, motif…" data-filter="#appointments" aria-label="Rechercher">
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<div class="card-soft table-wrap">
			<% if (list.isEmpty()) { %>
			<div class="empty-state"><i class="far fa-calendar"></i><h3>Aucun rendez-vous</h3></div>
			<% } else { %>
			<div class="table-responsive">
				<table class="table table-modern" id="appointments">
					<thead><tr><th>Patient</th><th>Contact</th><th>Date</th><th>Médecin</th><th>Motif</th><th>Statut</th></tr></thead>
					<tbody>
					<% for (Appointment ap : list) {
						Doctor d = doctors.getDoctorById(ap.getDoctorId());
						boolean pending = "Pending".equals(ap.getStatus()); %>
						<tr>
							<td class="cell-main"><%=Html.esc(ap.getFullName())%>
								<span class="cell-sub"><%="female".equals(ap.getGender()) ? "Femme" : "male".equals(ap.getGender()) ? "Homme" : Html.esc(ap.getGender())%>, <%=Html.esc(ap.getAge())%> ans</span></td>
							<td><%=Html.esc(ap.getEmail())%><span class="cell-sub"><%=Html.esc(ap.getPhNo())%></span></td>
							<td class="nowrap"><%=Html.esc(ap.getAppoinDate())%></td>
							<td><%=d == null ? "<span class=\"text-muted\">Médecin supprimé</span>" : "Dr " + Html.esc(d.getFullName())%>
								<span class="cell-sub"><%=d == null ? "" : Html.esc(d.getSpecialist())%></span></td>
							<td><%=Html.esc(ap.getDiseases())%><span class="cell-sub"><%=Html.esc(ap.getAddress())%></span></td>
							<td><% if (pending) { %><span class="status status-pending">En attente</span>
								<% } else { %><span class="status status-done">Traité</span><span class="status-note"><%=Html.esc(ap.getStatus())%></span><% } %></td>
						</tr>
					<% } %>
					</tbody>
				</table>
			</div>
			<div id="appointments-noresult" class="empty-state" hidden><i class="fas fa-search"></i><h3>Aucun résultat</h3></div>
			<% } %>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
