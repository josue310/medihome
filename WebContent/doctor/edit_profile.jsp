<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="com.db.DBConnect, com.dao.SpecialistDAO, com.entity.Specalist, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty doctObj}">
	<c:redirect url="../doctor_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Mon profil · Espace médecin</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-doctor">
	<c:set var="nav" value="profile" />
	<%@include file="/WEB-INF/jspf/navbar-doctor.jspf"%>
	<% Doctor me = (Doctor) session.getAttribute("doctObj"); %>
	<main class="app-main container">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/doctor/index.jsp">Tableau de bord</a> / Mon profil</div>
				<h1>Mon profil</h1>
				<p>Vos informations professionnelles, visibles par les patients lors de la prise de rendez-vous.</p>
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<div class="row g-4">
			<div class="col-lg-7">
				<form class="card-soft" action="../doctorUpdateProfile" method="post">
					<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
					<div class="card-body">
						<div class="card-title-row"><h2><i class="fas fa-id-badge text-muted"></i> Informations</h2></div>
						<div class="row g-3">
							<div class="col-md-7">
								<label class="form-label" for="fullname">Nom complet</label>
								<input id="fullname" name="fullname" type="text" class="form-control" value="${fn:escapeXml(doctObj.fullName)}" required>
							</div>
							<div class="col-md-5">
								<label class="form-label" for="dob">Date de naissance</label>
								<input id="dob" name="dob" type="date" class="form-control" value="${fn:escapeXml(doctObj.dob)}" required>
							</div>
							<div class="col-md-6">
								<label class="form-label" for="qualification">Diplôme / qualification</label>
								<input id="qualification" name="qualification" type="text" class="form-control" value="${fn:escapeXml(doctObj.qualification)}" required>
							</div>
							<div class="col-md-6">
								<label class="form-label" for="spec">Spécialité</label>
								<select id="spec" name="spec" class="form-select" required>
									<% boolean found = false;
									for (Specalist s : new SpecialistDAO(DBConnect.getConn()).getAllSpecialist()) {
										boolean sel = s.getSpecialistName().equals(me.getSpecialist());
										found = found || sel; %>
									<option <%=sel ? "selected" : ""%>><%=Html.esc(s.getSpecialistName())%></option>
									<% }
									if (!found && me.getSpecialist() != null) { %>
									<option selected><%=Html.esc(me.getSpecialist())%></option>
									<% } %>
								</select>
							</div>
							<div class="col-md-6">
								<label class="form-label" for="email">E-mail (identifiant)</label>
								<input id="email" name="email" type="email" class="form-control" value="${fn:escapeXml(doctObj.email)}" readonly>
								<div class="form-hint">Modifiable uniquement par l'administration.</div>
							</div>
							<div class="col-md-6">
								<label class="form-label" for="mobno">Téléphone</label>
								<input id="mobno" name="mobno" type="tel" class="form-control" value="${fn:escapeXml(doctObj.mobNo)}" required>
							</div>
						</div>
						<div class="text-end mt-4"><button type="submit" class="btn btn-primary"><i class="fas fa-save"></i> Enregistrer</button></div>
					</div>
				</form>
			</div>
			<div class="col-lg-5">
				<form class="card-soft" action="../doctChangePassword" method="post">
					<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
					<div class="card-body">
						<div class="card-title-row"><h2><i class="fas fa-key text-muted"></i> Mot de passe</h2></div>
						<div class="mb-3">
							<label class="form-label" for="oldPassword">Mot de passe actuel</label>
							<input id="oldPassword" name="oldPassword" type="password" class="form-control" autocomplete="current-password" required>
						</div>
						<div class="mb-3">
							<label class="form-label" for="password">Nouveau mot de passe</label>
							<input id="password" name="newPassword" type="password" class="form-control" autocomplete="new-password" minlength="8" maxlength="128" required>
						</div>
						<div class="mb-4">
							<label class="form-label" for="password2">Confirmation</label>
							<input id="password2" type="password" class="form-control" autocomplete="new-password" minlength="8" maxlength="128" required>
						</div>
						<button type="submit" class="btn btn-primary w-100">Mettre à jour le mot de passe</button>
					</div>
				</form>
			</div>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
