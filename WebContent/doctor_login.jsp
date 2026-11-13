<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Connexion médecin · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-doctor">
	<c:set var="nav" value="login" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>
	<main class="app-main container">
		<div class="card-soft auth-wrap">
			<aside class="auth-aside">
				<span class="brand-mark"><i class="fas fa-user-md"></i></span>
				<h2>Espace médecin</h2>
				<p>Consultez vos patients, rédigez vos comptes rendus et menez vos téléconsultations.</p>
				<ul>
					<li><i class="fas fa-check-circle"></i> Liste de vos patients et rendez-vous</li>
					<li><i class="fas fa-check-circle"></i> Comptes rendus de consultation</li>
					<li><i class="fas fa-check-circle"></i> Téléconsultation et partage de documents</li>
				</ul>
			</aside>
			<section class="auth-form">
				<h1>Connexion médecin</h1>
				<p class="text-muted mb-4">Saisissez vos identifiants pour continuer.</p>
				<%@include file="/WEB-INF/jspf/flash.jspf"%>
				<form action="doctorLogin" method="post">
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
				<div class="auth-roles"><a href="${ctx}/user_login.jsp"><i class="fas fa-user"></i> Espace patient</a> &middot; <a href="${ctx}/admin_login.jsp"><i class="fas fa-user-shield"></i> Administration</a></div>
			</section>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
