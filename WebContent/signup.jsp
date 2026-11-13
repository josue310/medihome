<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Créer un compte · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-patient">
	<c:set var="nav" value="signup" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>
	<main class="app-main container">
		<div class="card-soft auth-wrap">
			<aside class="auth-aside">
				<span class="brand-mark"><i class="fas fa-user-plus"></i></span>
				<h2>Rejoignez Medi Home</h2>
				<p>Créez votre compte patient en moins d'une minute.</p>
				<ul>
					<li><i class="fas fa-check-circle"></i> Rendez-vous avec nos spécialistes</li>
					<li><i class="fas fa-check-circle"></i> Consultations en vidéo depuis chez vous</li>
					<li><i class="fas fa-check-circle"></i> Mot de passe chiffré, données protégées</li>
				</ul>
			</aside>
			<section class="auth-form">
				<h1>Créer un compte patient</h1>
				<p class="text-muted mb-4">Tous les champs sont obligatoires.</p>
				<%@include file="/WEB-INF/jspf/flash.jspf"%>
				<form action="user_register" method="post" id="signup-form">
					<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
					<div class="mb-3">
						<label class="form-label" for="fullname">Nom complet</label>
						<input id="fullname" name="fullname" type="text" class="form-control" autocomplete="name" maxlength="100" required autofocus>
					</div>
					<div class="mb-3">
						<label class="form-label" for="email">Adresse e-mail</label>
						<input id="email" name="email" type="email" class="form-control" autocomplete="email" maxlength="100" required>
					</div>
					<div class="row g-3 mb-4">
						<div class="col-sm-6">
							<label class="form-label" for="password">Mot de passe</label>
							<input id="password" name="password" type="password" class="form-control" autocomplete="new-password" minlength="8" maxlength="128" required>
							<div class="form-hint">8 caractères minimum.</div>
						</div>
						<div class="col-sm-6">
							<label class="form-label" for="password2">Confirmation</label>
							<input id="password2" type="password" class="form-control" autocomplete="new-password" minlength="8" maxlength="128" required>
						</div>
					</div>
					<button type="submit" class="btn btn-primary btn-lg w-100"><i class="fas fa-user-plus"></i> Créer mon compte</button>
				</form>
				<p class="auth-switch">Déjà inscrit(e) ? <a href="${ctx}/user_login.jsp">Se connecter</a></p>
			</section>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
