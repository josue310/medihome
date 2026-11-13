<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Connexion administrateur · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-admin">
	<c:set var="nav" value="login" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>
	<main class="app-main container">
		<div class="card-soft auth-wrap">
			<aside class="auth-aside">
				<span class="brand-mark"><i class="fas fa-user-shield"></i></span>
				<h2>Administration</h2>
				<p>Pilotez l'établissement : médecins, spécialités et activité des rendez-vous.</p>
				<ul>
					<li><i class="fas fa-check-circle"></i> Gestion des médecins</li>
					<li><i class="fas fa-check-circle"></i> Gestion des spécialités</li>
					<li><i class="fas fa-check-circle"></i> Suivi de tous les rendez-vous</li>
				</ul>
			</aside>
			<section class="auth-form">
				<h1>Connexion administrateur</h1>
				<p class="text-muted mb-4">Saisissez vos identifiants pour continuer.</p>
				<%@include file="/WEB-INF/jspf/flash.jspf"%>
				<form action="adminLogin" method="post">
					<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
					<div class="mb-3">
						<label class="form-label" for="email">Adresse e-mail</label>
						<input id="email" name="email" type="email" class="form-control" autocomplete="username" required autofocus>
					</div>
					<div class="mb-4">
						<label class="form-label" for="password">Mot de passe</label>
						<input id="password" name="password" type="password" class="form-control" autocomplete="current-password" required>
					</div>
					<button type="submit" class="btn btn-primary btn-lg w-100"><i class="fas fa-sign-in-alt"></i> Se connecter</button>
				</form>
				<div class="auth-roles"><a href="${ctx}/user_login.jsp"><i class="fas fa-user"></i> Espace patient</a> &middot; <a href="${ctx}/doctor_login.jsp"><i class="fas fa-user-md"></i> Espace médecin</a></div>
			</section>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
