<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, com.db.DBConnect, com.dao.DoctorDAO, com.entity.Doctor, com.util.Html"%>
<c:if test="${empty userObj}">
	<c:redirect url="user_login.jsp" />
</c:if>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Prendre rendez-vous · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-patient">
	<c:set var="nav" value="book" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>
	<main class="app-main container">
		<div class="page-header">
			<div>
				<div class="breadcrumb-lite"><a href="${ctx}/index.jsp">Mon espace</a> / Prendre rendez-vous</div>
				<h1>Prendre rendez-vous</h1>
				<p>Choisissez votre médecin et la date souhaitée. Vous pourrez ensuite rejoindre la téléconsultation depuis « Mes rendez-vous ».</p>
			</div>
		</div>
		<%@include file="/WEB-INF/jspf/flash.jspf"%>
		<div class="row g-4">
			<div class="col-lg-8">
				<form class="card-soft" action="appAppointment" method="post">
					<input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
					<div class="card-body">
						<p class="form-section-title">Consultation</p>
						<div class="row g-3 mb-3">
							<div class="col-md-7">
								<label class="form-label" for="doct">Médecin</label>
								<select id="doct" name="doct" class="form-select" required>
									<option value="">Choisir un médecin…</option>
									<% for (Doctor d : new DoctorDAO(DBConnect.getConn()).getAllDoctor()) { %>
									<option value="<%=d.getId()%>">Dr <%=Html.esc(d.getFullName())%> — <%=Html.esc(d.getSpecialist())%></option>
									<% } %>
								</select>
							</div>
							<div class="col-md-5">
								<label class="form-label" for="appoint_date">Date souhaitée</label>
								<input id="appoint_date" name="appoint_date" type="date" class="form-control" data-min-today required>
							</div>
							<div class="col-12">
								<label class="form-label" for="diseases">Motif de consultation</label>
								<input id="diseases" name="diseases" type="text" class="form-control" maxlength="200" placeholder="Ex. : douleurs lombaires, renouvellement d'ordonnance…" required>
							</div>
						</div>

						<p class="form-section-title">Patient</p>
						<div class="row g-3 mb-3">
							<div class="col-md-6">
								<label class="form-label" for="fullname">Nom complet</label>
								<input id="fullname" name="fullname" type="text" class="form-control" value="${fn:escapeXml(userObj.fullName)}" required>
							</div>
							<div class="col-md-3">
								<label class="form-label" for="gender">Sexe</label>
								<select id="gender" name="gender" class="form-select" required>
									<option value="female">Femme</option>
									<option value="male">Homme</option>
								</select>
							</div>
							<div class="col-md-3">
								<label class="form-label" for="age">Âge</label>
								<input id="age" name="age" type="number" class="form-control" min="0" max="130" required>
							</div>
							<div class="col-md-6">
								<label class="form-label" for="email">E-mail</label>
								<input id="email" name="email" type="email" class="form-control" value="${fn:escapeXml(userObj.email)}" required>
							</div>
							<div class="col-md-6">
								<label class="form-label" for="phno">Téléphone</label>
								<input id="phno" name="phno" type="tel" class="form-control" pattern="[0-9 +]{8,15}" placeholder="06 12 34 56 78" required>
							</div>
							<div class="col-12">
								<label class="form-label" for="address">Adresse</label>
								<textarea id="address" name="address" class="form-control" rows="2" required></textarea>
							</div>
						</div>
						<div class="d-flex flex-wrap gap-2 justify-content-end">
							<a href="${ctx}/index.jsp" class="btn btn-light-border">Annuler</a>
							<button type="submit" class="btn btn-primary"><i class="fas fa-check"></i> Confirmer le rendez-vous</button>
						</div>
					</div>
				</form>
			</div>
			<div class="col-lg-4">
				<div class="card-soft">
					<div class="card-body">
						<h2 class="h6 fw-bold mb-3"><i class="fas fa-info-circle text-muted"></i> Comment ça marche ?</h2>
						<ol class="ps-3 mb-0 text-muted small d-grid gap-2">
							<li>Votre demande apparaît dans « Mes rendez-vous » avec le statut <span class="status status-pending">En attente</span>.</li>
							<li>Le jour venu, cliquez sur <strong>Rejoindre</strong> pour la téléconsultation vidéo.</li>
							<li>Après la consultation, le compte rendu du médecin s'affiche dans votre espace.</li>
						</ol>
					</div>
				</div>
			</div>
		</div>
	</main>
	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
