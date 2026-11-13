<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page import="java.util.List, java.util.ArrayList"%>
<%@ page import="com.db.DBConnect, com.dao.AppointmentDAO, com.dao.DoctorDAO"%>
<%@ page import="com.entity.Appointment, com.entity.Doctor, com.entity.User, com.util.Html"%>
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>${empty userObj ? 'Accueil' : 'Mon espace'} · Medi Home</title>
<%@include file="/WEB-INF/jspf/head.jspf"%>
</head>
<body class="app-body role-patient">
	<c:set var="nav" value="home" />
	<%@include file="/WEB-INF/jspf/navbar.jspf"%>

	<c:choose>
		<%-- ===================== Visiteur : page d'accueil ===================== --%>
		<c:when test="${empty userObj}">
			<main>
				<section class="hero">
					<div class="container">
						<div class="hero-inner">
							<h1>Votre santé, suivie simplement — au cabinet ou à distance.</h1>
							<p class="lead">Prenez rendez-vous en ligne avec nos médecins spécialistes et consultez-les en vidéo
								depuis chez vous, en toute confidentialité.</p>
							<div class="d-flex flex-wrap gap-2">
								<a href="${ctx}/signup.jsp" class="btn btn-light btn-lg"><i class="fas fa-user-plus"></i> Créer mon compte patient</a>
								<a href="${ctx}/user_login.jsp" class="btn btn-outline-light btn-lg">J'ai déjà un compte</a>
							</div>
							<div class="hero-badges">
								<span><i class="fas fa-shield-alt"></i> Données protégées</span>
								<span><i class="fas fa-video"></i> Téléconsultation sécurisée</span>
								<span><i class="far fa-clock"></i> Rendez-vous 24 h/24</span>
							</div>
						</div>
					</div>
				</section>

				<section class="section">
					<div class="container">
						<div class="section-title">
							<h2>Un parcours de soins pensé pour vous</h2>
							<p>De la prise de rendez-vous au compte rendu du médecin, tout se passe au même endroit.</p>
						</div>
						<div class="row g-4">
							<div class="col-sm-6 col-lg-3">
								<div class="card-soft feature-card">
									<div class="stat-icon"><i class="far fa-calendar-plus"></i></div>
									<h3>Rendez-vous en ligne</h3>
									<p>Choisissez votre médecin et votre date en quelques clics, sans attente au téléphone.</p>
								</div>
							</div>
							<div class="col-sm-6 col-lg-3">
								<div class="card-soft feature-card">
									<div class="stat-icon tone-blue"><i class="fas fa-video"></i></div>
									<h3>Téléconsultation</h3>
									<p>Échangez en vidéo avec votre médecin, partagez vos ordonnances et résultats d'analyse.</p>
								</div>
							</div>
							<div class="col-sm-6 col-lg-3">
								<div class="card-soft feature-card">
									<div class="stat-icon tone-violet"><i class="fas fa-user-md"></i></div>
									<h3>Médecins spécialistes</h3>
									<p>Une équipe pluridisciplinaire à votre écoute, du généraliste au spécialiste.</p>
								</div>
							</div>
							<div class="col-sm-6 col-lg-3">
								<div class="card-soft feature-card">
									<div class="stat-icon tone-green"><i class="fas fa-lock"></i></div>
									<h3>Confidentialité</h3>
									<p>Mots de passe chiffrés, échanges protégés : vos données médicales restent privées.</p>
								</div>
							</div>
						</div>
					</div>
				</section>

				<section class="section pt-0">
					<div class="container">
						<div class="section-title">
							<h2>Notre équipe médicale</h2>
							<p>Des professionnels expérimentés qui vous accompagnent à chaque étape.</p>
						</div>
						<div class="row g-4">
							<div class="col-6 col-lg-3"><div class="card-soft team-card"><img src="${ctx}/img/doc1.jpg" alt="Samuani Simi"><div class="card-body"><h3>Samuani Simi</h3><p>Directrice générale</p></div></div></div>
							<div class="col-6 col-lg-3"><div class="card-soft team-card"><img src="${ctx}/img/doc2.jpg" alt="Dr Siva Kumar"><div class="card-body"><h3>Dr Siva Kumar</h3><p>Médecin chef</p></div></div></div>
							<div class="col-6 col-lg-3"><div class="card-soft team-card"><img src="${ctx}/img/doc3.jpg" alt="Dr Niuise Paule"><div class="card-body"><h3>Dr Niuise Paule</h3><p>Médecin chef</p></div></div></div>
							<div class="col-6 col-lg-3"><div class="card-soft team-card"><img src="${ctx}/img/doc4.jpg" alt="Dr Mathue Samuel"><div class="card-body"><h3>Dr Mathue Samuel</h3><p>Médecin chef</p></div></div></div>
						</div>
					</div>
				</section>

				<section class="section spaces">
					<div class="container">
						<div class="row g-4">
							<div class="col-md-4">
								<a class="card-soft action-tile" href="${ctx}/user_login.jsp">
									<span class="stat-icon"><i class="fas fa-user"></i></span>
									<span><strong>Espace patient</strong><small>Rendez-vous et téléconsultations</small></span>
								</a>
							</div>
							<div class="col-md-4">
								<a class="card-soft action-tile" href="${ctx}/doctor_login.jsp">
									<span class="stat-icon tone-blue"><i class="fas fa-user-md"></i></span>
									<span><strong>Espace médecin</strong><small>Patients, comptes rendus, consultations</small></span>
								</a>
							</div>
							<div class="col-md-4">
								<a class="card-soft action-tile" href="${ctx}/admin_login.jsp">
									<span class="stat-icon tone-violet"><i class="fas fa-user-shield"></i></span>
									<span><strong>Administration</strong><small>Médecins, spécialités, activité</small></span>
								</a>
							</div>
						</div>
					</div>
				</section>
			</main>
		</c:when>

		<%-- ===================== Patient connecté : tableau de bord ===================== --%>
		<c:otherwise>
			<%
				User me = (User) session.getAttribute("userObj");
				List<Appointment> mine = new AppointmentDAO(DBConnect.getConn()).getAllAppointmentByLoginUser(me.getId());
				DoctorDAO doctors = new DoctorDAO(DBConnect.getConn());
				int pending = 0;
				List<Appointment> upcoming = new ArrayList<Appointment>();
				for (Appointment a : mine) {
					if ("Pending".equals(a.getStatus())) {
						pending++;
						if (upcoming.size() < 4) {
							upcoming.add(a);
						}
					}
				}
			%>
			<main class="app-main container">
				<div class="card-soft welcome-banner mb-4 d-flex flex-wrap align-items-center justify-content-between gap-3">
					<div>
						<h1>Bonjour ${fn:escapeXml(userObj.fullName)}</h1>
						<p>Retrouvez vos rendez-vous et rejoignez vos téléconsultations depuis votre espace.</p>
					</div>
					<a href="${ctx}/user_appointment.jsp" class="btn btn-light btn-lg"><i class="far fa-calendar-plus"></i> Prendre rendez-vous</a>
				</div>

				<%@include file="/WEB-INF/jspf/flash.jspf"%>

				<div class="row g-3 mb-4">
					<div class="col-md-4">
						<div class="card-soft stat-card">
							<span class="stat-icon"><i class="far fa-calendar-alt"></i></span>
							<div><div class="stat-value"><%=mine.size()%></div><div class="stat-label">Rendez-vous au total</div></div>
						</div>
					</div>
					<div class="col-md-4">
						<div class="card-soft stat-card">
							<span class="stat-icon tone-amber"><i class="far fa-hourglass"></i></span>
							<div><div class="stat-value"><%=pending%></div><div class="stat-label">En attente de consultation</div></div>
						</div>
					</div>
					<div class="col-md-4">
						<div class="card-soft stat-card">
							<span class="stat-icon tone-green"><i class="fas fa-file-medical"></i></span>
							<div><div class="stat-value"><%=mine.size() - pending%></div><div class="stat-label">Consultations traitées</div></div>
						</div>
					</div>
				</div>

				<div class="row g-4">
					<div class="col-lg-8">
						<div class="card-soft">
							<div class="card-body">
								<div class="card-title-row">
									<h2>Prochains rendez-vous</h2>
									<a href="${ctx}/view_appointment.jsp" class="btn btn-sm btn-soft">Tout voir</a>
								</div>
								<% if (upcoming.isEmpty()) { %>
								<div class="empty-state">
									<i class="far fa-calendar"></i>
									<h3>Aucun rendez-vous à venir</h3>
									<p>Planifiez une consultation avec l'un de nos médecins.</p>
									<a href="${ctx}/user_appointment.jsp" class="btn btn-primary">Prendre rendez-vous</a>
								</div>
								<% } else { %>
								<div class="table-responsive">
									<table class="table table-modern">
										<thead><tr><th>Date</th><th>Médecin</th><th>Motif</th><th class="text-end">Téléconsultation</th></tr></thead>
										<tbody>
										<% for (Appointment a : upcoming) {
											Doctor d = doctors.getDoctorById(a.getDoctorId()); %>
											<tr>
												<td class="cell-main nowrap"><%=Html.esc(a.getAppoinDate())%></td>
												<td><%=d == null ? "—" : "Dr " + Html.esc(d.getFullName())%>
													<span class="cell-sub"><%=d == null ? "" : Html.esc(d.getSpecialist())%></span></td>
												<td><%=Html.esc(a.getDiseases())%></td>
												<td class="text-end"><a class="btn btn-sm btn-tele" href="${ctx}/teleconsultation/consultation.jsp?id=<%=a.getId()%>"><i class="fas fa-video"></i> Rejoindre</a></td>
											</tr>
										<% } %>
										</tbody>
									</table>
								</div>
								<% } %>
							</div>
						</div>
					</div>
					<div class="col-lg-4">
						<div class="d-grid gap-3">
							<a class="card-soft action-tile" href="${ctx}/user_appointment.jsp">
								<span class="stat-icon"><i class="far fa-calendar-plus"></i></span>
								<span><strong>Nouveau rendez-vous</strong><small>Choisir un médecin et une date</small></span>
							</a>
							<a class="card-soft action-tile" href="${ctx}/view_appointment.jsp">
								<span class="stat-icon tone-blue"><i class="fas fa-notes-medical"></i></span>
								<span><strong>Mes rendez-vous</strong><small>Statuts et comptes rendus</small></span>
							</a>
							<a class="card-soft action-tile" href="${ctx}/change_password.jsp">
								<span class="stat-icon tone-violet"><i class="fas fa-key"></i></span>
								<span><strong>Sécurité du compte</strong><small>Changer mon mot de passe</small></span>
							</a>
						</div>
					</div>
				</div>
			</main>
		</c:otherwise>
	</c:choose>

	<%@include file="/WEB-INF/jspf/footer.jspf"%>
</body>
</html>
