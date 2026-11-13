<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Connexion patient · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-patient">
	<c:set var="nav" value="login" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>
	<main class="app-main container">
		<div class="card-soft auth-wrap">
			<aside class="auth-aside">
				<span class="brand-mark"><i class="fas fa-user"></i></span>
				<h2>Votre espace patient</h2>
				<p>Gérez vos rendez-vous et retrouvez vos médecins en téléconsultation.</p>
				<ul>
					<li><i class="fas fa-check-circle"></i> Prise de rendez-vous en ligne</li>
					<li><i class="fas fa-check-circle"></i> Téléconsultation vidéo sécurisée</li>
					<li><i class="fas fa-check-circle"></i> Partage d'ordonnances et de résultats</li>
				</ul>
			</aside>
			<section class="auth-form">
				<h1>Connexion patient</h1>
				<p class="text-muted mb-4">Saisissez vos identifiants pour continuer.</p>
				<%@include file="/WEB-INF/jspf/flash.jspf"%>
				<form action="userLogin" method="post">
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
				<p class="auth-switch">Pas encore de compte ? <a href="${ctx}/signup.jsp">Créer un compte patient</a></p>
				<div class="auth-roles"><a href="${ctx}/doctor_login.jsp"><i class="fas fa-user-md"></i> Espace médecin</a> &middot; <a href="${ctx}/admin_login.jsp"><i class="fas fa-user-shield"></i> Administration</a></div>
			</section>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
