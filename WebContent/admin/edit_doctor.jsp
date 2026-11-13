<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="com.db.DBConnect, com.dao.DoctorDAO, com.dao.SpecialistDAO, com.entity.Specalist, com.entity.Doctor, com.util.Html"%>
<%
	if (session.getAttribute("adminObj") == null) {
		response.sendRedirect("../admin_login.jsp");
		return;
	}
	Doctor editDoctor = null;
	try {
		editDoctor = new DoctorDAO(DBConnect.getConn()).getDoctorById(Integer.parseInt(request.getParameter("id")));
	} catch (NumberFormatException e) {
		editDoctor = null;
	}
	if (editDoctor == null) {
		session.setAttribute("errorMsg", "Médecin introuvable.");
		response.sendRedirect("view_doctor.jsp");
		return;
	}
%>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Modifier un médecin · Administration</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-admin">
	<c:set var="nav" value="doctors" />
	<%@include file="/WEB-INF/jspf/navbar-admin.jspf"%>
	<main class="app-main container" style="max-width: 900px">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/admin/view_doctor.jsp">Médecins</a> / Modifier</div>
				<h1>Dr <%=Html.esc(editDoctor.getFullName())%></h1>
				<p>Modifiez les informations du médecin.</p>
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<form class="card-soft" action="../updateDoctor" method="post">
			<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
			<input type="hidden" name="id" value="<%=editDoctor.getId()%>">
			<div class="card-body">
				<%@include file="/WEB-INF/jspf/doctor_form.jspf"%>
				<div class="d-flex flex-wrap gap-2 justify-content-end mt-4">
					<a href="${ctx}/admin/view_doctor.jsp" class="btn btn-light-border">Annuler</a>
					<button type="submit" class="btn btn-primary"><i class="fas fa-save"></i> Enregistrer les modifications</button>
				</div>
			</div>
		</form>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
