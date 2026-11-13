<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<c:if test="${empty userObj}">
	<c:redirect url="user_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Changer le mot de passe · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-patient">
	<c:set var="nav" value="password" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>
	<main class="app-main container" style="max-width: 560px">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/index.jsp">Mon espace</a> / Sécurité</div>
				<h1>Changer le mot de passe</h1>
				<p>Choisissez un mot de passe d'au moins 8 caractères, différent des précédents.</p>
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<form class="card-soft" action="userChangePassword" method="post">
			<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
			<div class="card-body">
				<div class="mb-3">
					<label class="form-label" for="oldPassword">Mot de passe actuel</label>
					<input id="oldPassword" name="oldPassword" type="password" class="form-control" autocomplete="current-password" required>
				</div>
				<div class="mb-3">
					<label class="form-label" for="password">Nouveau mot de passe</label>
					<input id="password" name="newPassword" type="password" class="form-control" autocomplete="new-password" minlength="8" maxlength="128" required>
				</div>
				<div class="mb-4">
					<label class="form-label" for="password2">Confirmer le nouveau mot de passe</label>
					<input id="password2" type="password" class="form-control" autocomplete="new-password" minlength="8" maxlength="128" required>
				</div>
				<button type="submit" class="btn btn-primary w-100"><i class="fas fa-key"></i> Mettre à jour le mot de passe</button>
			</div>
		</form>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
