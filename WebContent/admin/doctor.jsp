<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="com.db.DBConnect, com.dao.SpecialistDAO, com.entity.Specalist, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty adminObj}">
	<c:redirect url="../admin_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Ajouter un médecin · Administration</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-admin">
	<c:set var="nav" value="doctors" />
	<%@include file="/WEB-INF/jspf/navbar-admin.jspf"%>
	<% Doctor editDoctor = null; %>
	<main class="app-main container" style="max-width: 900px">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/admin/view_doctor.jsp">Médecins</a> / Ajouter</div>
				<h1>Ajouter un médecin</h1>
				<p>Le médecin pourra se connecter à l'espace médecin avec son e-mail et ce mot de passe.</p>
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<form class="card-soft" action="../addDoctor" method="post">
			<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
			<div class="card-body">
				<%@include file="/WEB-INF/jspf/doctor_form.jspf"%>
				<div class="d-flex flex-wrap gap-2 justify-content-end mt-4">
					<a href="${ctx}/admin/view_doctor.jsp" class="btn btn-light-border">Annuler</a>
					<button type="submit" class="btn btn-primary"><i class="fas fa-user-plus"></i> Créer le médecin</button>
				</div>
			</div>
		</form>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
