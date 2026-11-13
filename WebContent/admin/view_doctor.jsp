<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, com.db.DBConnect, com.dao.DoctorDAO, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty adminObj}">
	<c:redirect url="../admin_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Médecins · Administration</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-admin">
	<c:set var="nav" value="doctors" />
	<%@include file="/WEB-INF/jspf/navbar-admin.jspf"%>
	<% List<Doctor> list = new DoctorDAO(DBConnect.getConn()).getAllDoctor(); %>
	<main class="app-main container">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/admin/index.jsp">Tableau de bord</a> / Médecins</div>
				<h1>Médecins</h1>
				<p><%=list.size()%> médecin(s) enregistré(s).</p>
			</div>
			<div class="header-actions">
				<div class="search-box">
					<i class="fas fa-search"></i>
					<input type="search" class="form-control" placeholder="Rechercher…" data-filter="#doctors" aria-label="Rechercher un médecin">
				</div>
				<a href="${ctx}/admin/doctor.jsp" class="btn btn-primary"><i class="fas fa-user-plus"></i> Ajouter</a>
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<div class="card-soft table-wrap">
			<% if (list.isEmpty()) { %>
			<div class="empty-state"><i class="fas fa-user-md"></i><h3>Aucun médecin</h3><p>Commencez par ajouter un médecin.</p>
				<a href="${ctx}/admin/doctor.jsp" class="btn btn-primary">Ajouter un médecin</a></div>
			<% } else { %>
			<div class="table-responsive">
				<table class="table table-modern" id="doctors">
					<thead><tr><th>Médecin</th><th>Spécialité</th><th>Contact</th><th>Naissance</th><th class="text-end">Actions</th></tr></thead>
					<tbody>
					<% for (Doctor d : list) { %>
						<tr>
							<td class="cell-main">Dr <%=Html.esc(d.getFullName())%><span class="cell-sub"><%=Html.esc(d.getQualification())%></span></td>
							<td><span class="chip chip-accent"><%=Html.esc(d.getSpecialist())%></span></td>
							<td><%=Html.esc(d.getEmail())%><span class="cell-sub"><%=Html.esc(d.getMobNo())%></span></td>
							<td><%=Html.esc(d.getDob())%></td>
							<td><div class="table-actions">
								<a href="${ctx}/admin/edit_doctor.jsp?id=<%=d.getId()%>" class="btn btn-sm btn-light-border"><i class="fas fa-pen"></i> Modifier</a>
								<form action="../deleteDoctor" method="post" class="d-inline"
									onsubmit="return confirm('Supprimer définitivement ce médecin ?');">
									<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
									<input type="hidden" name="id" value="<%=d.getId()%>">
									<button type="submit" class="btn btn-sm btn-outline-danger"><i class="fas fa-trash-alt"></i> Supprimer</button>
								</form>
							</div></td>
						</tr>
					<% } %>
					</tbody>
				</table>
			</div>
			<div id="doctors-noresult" class="empty-state" hidden><i class="fas fa-search"></i><h3>Aucun résultat</h3></div>
			<% } %>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
