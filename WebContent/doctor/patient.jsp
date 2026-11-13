<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, com.db.DBConnect, com.dao.AppointmentDAO, com.entity.Appointment, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty doctObj}">
	<c:redirect url="../doctor_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Mes patients · Espace médecin</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-doctor">
	<c:set var="nav" value="patients" />
	<%@include file="/WEB-INF/jspf/navbar-doctor.jspf"%>
	<%
		Doctor me = (Doctor) session.getAttribute("doctObj");
		List<Appointment> list = new AppointmentDAO(DBConnect.getConn()).getAllAppointmentByDoctorLogin(me.getId());
	%>
	<main class="app-main container">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/doctor/index.jsp">Tableau de bord</a> / Mes patients</div>
				<h1>Mes patients</h1>
				<p><%=list.size()%> rendez-vous. Lancez une téléconsultation ou rédigez le compte rendu.</p>
			</div>
			<div class="search-box">
				<i class="fas fa-search"></i>
				<input type="search" class="form-control" placeholder="Rechercher un patient, un motif…" data-filter="#patients" aria-label="Rechercher">
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<div class="card-soft table-wrap">
			<% if (list.isEmpty()) { %>
			<div class="empty-state"><i class="fas fa-user-injured"></i><h3>Aucun patient pour le moment</h3><p>Les rendez-vous pris avec vous apparaîtront ici.</p></div>
			<% } else { %>
			<div class="table-responsive">
				<table class="table table-modern" id="patients">
					<thead><tr><th>Patient</th><th>Date</th><th>Contact</th><th>Motif</th><th>Statut</th><th class="text-end">Actions</th></tr></thead>
					<tbody>
					<% for (Appointment ap : list) {
						boolean pending = "Pending".equals(ap.getStatus()); %>
						<tr>
							<td class="cell-main"><%=Html.esc(ap.getFullName())%>
								<span class="cell-sub"><%="female".equals(ap.getGender()) ? "Femme" : "male".equals(ap.getGender()) ? "Homme" : Html.esc(ap.getGender())%>, <%=Html.esc(ap.getAge())%> ans</span></td>
							<td class="nowrap"><%=Html.esc(ap.getAppoinDate())%></td>
							<td><%=Html.esc(ap.getEmail())%><span class="cell-sub"><%=Html.esc(ap.getPhNo())%></span></td>
							<td><%=Html.esc(ap.getDiseases())%></td>
							<td><% if (pending) { %><span class="status status-pending">En attente</span>
								<% } else { %><span class="status status-done">Traité</span><span class="status-note"><%=Html.esc(ap.getStatus())%></span><% } %></td>
							<td><div class="table-actions">
								<a class="btn btn-sm btn-tele" href="${ctx}/teleconsultation/consultation.jsp?id=<%=ap.getId()%>"><i class="fas fa-video"></i> Consulter</a>
								<% if (pending) { %>
								<a class="btn btn-sm btn-light-border" href="${ctx}/doctor/comment.jsp?id=<%=ap.getId()%>"><i class="fas fa-pen"></i> Compte rendu</a>
								<% } %>
							</div></td>
						</tr>
					<% } %>
					</tbody>
				</table>
			</div>
			<div id="patients-noresult" class="empty-state" hidden><i class="fas fa-search"></i><h3>Aucun résultat</h3></div>
			<% } %>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
