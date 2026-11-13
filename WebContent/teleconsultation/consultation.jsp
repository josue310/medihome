<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
	// Accès réservé aux patients et médecins connectés ; l'appartenance à la
	// consultation est vérifiée ensuite par l'API (/context) et la WebSocket.
	boolean isDoctor = session.getAttribute("doctObj") != null;
	boolean isPatient = session.getAttribute("userObj") != null;
	if (!isDoctor && !isPatient) {
		session.setAttribute("errorMsg", "Veuillez vous connecter pour rejoindre la téléconsultation.");
		response.sendRedirect(request.getContextPath() + "/user_login.jsp");
		return;
	}
	boolean asDoctor = isDoctor && !"PATIENT".equals(request.getParameter("role"));
	String backUrl = request.getContextPath() + (asDoctor ? "/doctor/patient.jsp" : "/view_appointment.jsp");
	String backLabel = asDoctor ? "Mes patients" : "Mes rendez-vous";
%>
<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <meta name="theme-color" content="#0f766e">
  <meta name="referrer" content="same-origin">
  <title>Téléconsultation</title>
  <link rel="stylesheet" href="consultation.css">
  <script type="module" src="app.js"></script>
</head>
<body>
  <!-- Icônes (sprite SVG réutilisé via <use>) -->
  <svg xmlns="http://www.w3.org/2000/svg" hidden>
    <symbol id="i-mic" viewBox="0 0 24 24"><path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3z"/><path d="M19 11a7 7 0 0 1-14 0M12 18v3M8 21h8"/></symbol>
    <symbol id="i-mic-off" viewBox="0 0 24 24"><path d="M3 3l18 18M9 9v3a3 3 0 0 0 5.1 2.1M15 9.3V6a3 3 0 0 0-5.7-1.3"/><path d="M19 11a7 7 0 0 1-1.2 3.9M5 11a7 7 0 0 0 10.7 5.9M12 18v3M8 21h8"/></symbol>
    <symbol id="i-cam" viewBox="0 0 24 24"><rect x="2" y="6" width="14" height="12" rx="2"/><path d="M16 10l6-3v10l-6-3z"/></symbol>
    <symbol id="i-cam-off" viewBox="0 0 24 24"><path d="M3 3l18 18M16 16H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2M10 6h4a2 2 0 0 1 2 2v4l6-3v10l-2-1"/></symbol>
    <symbol id="i-phone" viewBox="0 0 24 24"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.8 2z"/></symbol>
    <symbol id="i-hangup" viewBox="0 0 24 24"><path d="M3.7 14.6l2.5-1.3a2 2 0 0 0 1-2.3l-.4-1.5a13 13 0 0 1 10.4 0l-.4 1.5a2 2 0 0 0 1 2.3l2.5 1.3a2 2 0 0 0 2.6-.7l.6-1a2 2 0 0 0-.3-2.5 17 17 0 0 0-22.4 0 2 2 0 0 0-.3 2.5l.6 1a2 2 0 0 0 2.6.7z"/></symbol>
    <symbol id="i-screen" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4M9 10l3-3 3 3M12 7v6"/></symbol>
    <symbol id="i-audio" viewBox="0 0 24 24"><path d="M3 18v-6a9 9 0 0 1 18 0v6"/><path d="M21 19a2 2 0 0 1-2 2h-1v-6h3zM3 19a2 2 0 0 0 2 2h1v-6H3z"/></symbol>
    <symbol id="i-chat" viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></symbol>
    <symbol id="i-attach" viewBox="0 0 24 24"><path d="M21.4 11.1l-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5"/></symbol>
    <symbol id="i-send" viewBox="0 0 24 24"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></symbol>
    <symbol id="i-close" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12"/></symbol>
    <symbol id="i-file" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h5"/></symbol>
    <symbol id="i-download" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/></symbol>
    <symbol id="i-back" viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></symbol>
    <symbol id="i-stethoscope" viewBox="0 0 24 24"><path d="M5 3v6a5 5 0 0 0 10 0V3M5 3H3M15 3h2M10 14v2a5 5 0 0 0 10 0v-2"/><circle cx="20" cy="12" r="2"/></symbol>
  </svg>

  <header class="topbar">
    <a class="back-link" href="<%=backUrl%>" title="Retour : <%=backLabel%>"><svg class="icon" aria-hidden="true"><use href="#i-back"/></svg><span class="back-link__label"><%=backLabel%></span></a>
    <div class="brand">
      <svg class="icon brand__logo" aria-hidden="true"><use href="#i-stethoscope"/></svg>
      <span class="brand__name">Medi Home · Téléconsultation</span>
    </div>
    <div class="topbar__status">
      <span id="connection-status" class="status-pill" data-state="offline" role="status" aria-live="polite">Hors ligne</span>
      <span id="network-quality" class="signal" data-level="unknown" role="img" aria-label="Qualité réseau inconnue" title="Qualité réseau" hidden>
        <span></span><span></span><span></span>
      </span>
    </div>
    <div class="user-chip" id="user-chip" hidden>
      <span id="self-name" class="user-chip__name"></span>
      <span id="self-role" class="badge"></span>
    </div>
  </header>

  <main id="app">
    <!-- Chargement -->
    <section id="screen-loading" class="screen screen--center" aria-busy="true">
      <div class="spinner" aria-hidden="true"></div>
      <p>Préparation de la consultation…</p>
    </section>

    <!-- Erreur bloquante (non connecté, non autorisé, navigateur incompatible…) -->
    <section id="screen-error" class="screen screen--center" hidden>
      <div class="card card--narrow">
        <h1 id="error-title">Accès impossible</h1>
        <p id="error-message" class="muted"></p>
        <div id="error-actions" class="actions"></div>
      </div>
    </section>

    <!-- Étape A : sas de pré-test matériel -->
    <section id="screen-haircheck" class="screen" hidden aria-labelledby="haircheck-title">
      <div class="haircheck card">
        <div class="haircheck__preview">
          <div class="video-frame">
            <video id="preview-video" class="mirror" autoplay muted playsinline></video>
            <div id="preview-placeholder" class="video-placeholder" hidden>
              <svg class="icon icon--xl" aria-hidden="true"><use href="#i-cam-off"/></svg>
              <span>Caméra désactivée</span>
            </div>
            <div class="video-frame__toggles">
              <button id="preview-mic-btn" class="round-btn" type="button" aria-pressed="true" aria-label="Couper le micro">
                <svg class="icon" aria-hidden="true"><use href="#i-mic"/></svg>
              </button>
              <button id="preview-cam-btn" class="round-btn" type="button" aria-pressed="true" aria-label="Couper la caméra">
                <svg class="icon" aria-hidden="true"><use href="#i-cam"/></svg>
              </button>
            </div>
          </div>
        </div>

        <div class="haircheck__panel">
          <h1 id="haircheck-title">Vérifiez votre caméra et votre micro</h1>
          <p class="muted">Consultation n° <strong id="consultation-label"></strong>. Votre image n'est visible par personne tant que l'appel n'a pas commencé.</p>

          <div id="media-error" class="alert alert--error" role="alert" hidden>
            <p id="media-error-text"></p>
            <button id="retry-media-btn" class="btn btn--small" type="button">Réessayer</button>
          </div>
          <div id="media-warning" class="alert alert--warning" role="status" hidden></div>

          <div class="field">
            <label for="video-select">Caméra</label>
            <select id="video-select"></select>
          </div>
          <div class="field">
            <label for="audio-select">Microphone</label>
            <select id="audio-select"></select>
          </div>
          <div class="field">
            <span class="field__label" id="meter-label">Niveau du micro</span>
            <div class="meter" role="meter" aria-labelledby="meter-label" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0">
              <div id="mic-meter" class="meter__fill"></div>
            </div>
            <small class="muted">Parlez : la barre doit bouger.</small>
          </div>

          <button id="join-btn" class="btn btn--primary btn--lg btn--block" type="button" disabled>Rejoindre la salle d'attente</button>
        </div>
      </div>
    </section>

    <!-- Étape B : salle d'attente -->
    <section id="screen-waiting" class="screen" hidden aria-labelledby="waiting-title">
      <div class="waiting card">
        <h1 id="waiting-title">Salle d'attente</h1>
        <div class="participants">
          <article class="participant">
            <div class="avatar" id="self-avatar" aria-hidden="true"></div>
            <div>
              <p class="participant__name" id="self-card-name"></p>
              <p class="participant__status"><span class="dot dot--ok"></span>Vous êtes connecté(e)</p>
            </div>
          </article>
          <article class="participant" id="peer-card" data-present="false">
            <div class="avatar avatar--peer" id="peer-avatar" aria-hidden="true">?</div>
            <div>
              <p class="participant__name" id="peer-name"></p>
              <p class="participant__status" id="peer-status" role="status" aria-live="polite"><span class="dot"></span>Pas encore connecté(e)</p>
            </div>
          </article>
        </div>

        <video id="waiting-preview" class="mirror waiting__preview" autoplay muted playsinline></video>

        <div class="actions">
          <button id="call-btn" class="btn btn--primary btn--lg" type="button" disabled>
            <svg class="icon" aria-hidden="true"><use href="#i-cam"/></svg>
            Appel vidéo
          </button>
          <button id="audio-call-btn" class="btn btn--outline btn--lg" type="button" disabled>
            <svg class="icon" aria-hidden="true"><use href="#i-phone"/></svg>
            Appel audio
          </button>
          <button id="waiting-chat-btn" class="btn btn--ghost" type="button" aria-controls="chat-drawer">
            <svg class="icon" aria-hidden="true"><use href="#i-chat"/></svg>
            Messages &amp; documents <span class="unread" hidden></span>
          </button>
          <button id="leave-btn" class="btn btn--ghost" type="button">Quitter</button>
        </div>
      </div>

      <!-- Appel sortant (sonnerie côté appelant) -->
      <div id="outgoing-panel" class="outgoing" role="status" aria-live="polite" hidden>
        <div class="pulse-avatar" id="outgoing-avatar" aria-hidden="true"></div>
        <p class="outgoing__title" id="outgoing-title">Appel en cours…</p>
        <p class="muted" id="outgoing-countdown"></p>
        <button id="cancel-call-btn" class="btn btn--danger" type="button">Annuler</button>
      </div>
    </section>

    <!-- Étapes C/D : appel actif -->
    <section id="screen-call" class="call-stage" hidden aria-label="Appel en cours">
      <video id="remote-video" class="call-stage__remote" autoplay playsinline></video>
      <div id="remote-placeholder" class="call-stage__placeholder" hidden>
        <div class="avatar avatar--xl" id="remote-placeholder-avatar" aria-hidden="true"></div>
        <p>Caméra du correspondant coupée</p>
      </div>
      <div id="audio-only-view" class="audio-only" hidden>
        <div class="avatar avatar--xxl" id="audio-only-avatar" aria-hidden="true"></div>
        <p class="audio-only__name" id="audio-only-name"></p>
        <p class="audio-only__timer" id="audio-only-timer">00:00</p>
        <p class="muted-light" id="audio-only-hint">Appel audio</p>
      </div>

      <div class="call-stage__top">
        <span class="call-stage__peer" id="call-peer-name"></span>
        <time class="call-stage__timer" id="call-timer">00:00</time>
      </div>
      <div id="call-banner" class="call-banner" role="status" aria-live="assertive" hidden></div>

      <div id="local-pip" class="pip" tabindex="0" aria-label="Votre image (déplaçable)">
        <video id="local-video" class="mirror" autoplay muted playsinline></video>
        <span class="pip__label" id="pip-label">Vous</span>
      </div>

      <div id="degraded-hint" class="hint" role="alert" hidden>
        <span>Connexion faible. Passer en audio seul pour stabiliser l'appel ?</span>
        <button id="hint-audio-only-btn" class="btn btn--small btn--light" type="button">Audio seul</button>
        <button id="hint-close-btn" class="icon-btn" type="button" aria-label="Ignorer">
          <svg class="icon" aria-hidden="true"><use href="#i-close"/></svg>
        </button>
      </div>

      <nav class="controls" aria-label="Contrôles de l'appel">
        <button id="mic-btn" class="ctrl" type="button" aria-pressed="false" title="Couper le micro">
          <svg class="icon" aria-hidden="true"><use href="#i-mic"/></svg><span class="ctrl__label">Micro</span>
        </button>
        <button id="cam-btn" class="ctrl" type="button" aria-pressed="false" title="Couper la caméra">
          <svg class="icon" aria-hidden="true"><use href="#i-cam"/></svg><span class="ctrl__label">Caméra</span>
        </button>
        <button id="audio-only-btn" class="ctrl" type="button" aria-pressed="false" title="Mode audio seul (faible débit)">
          <svg class="icon" aria-hidden="true"><use href="#i-audio"/></svg><span class="ctrl__label" id="audio-only-label">Audio seul</span>
        </button>
        <button id="screen-btn" class="ctrl ctrl--desktop" type="button" aria-pressed="false" title="Partager l'écran">
          <svg class="icon" aria-hidden="true"><use href="#i-screen"/></svg><span class="ctrl__label">Écran</span>
        </button>
        <button id="call-chat-btn" class="ctrl" type="button" aria-controls="chat-drawer" title="Messages et documents">
          <svg class="icon" aria-hidden="true"><use href="#i-chat"/></svg><span class="ctrl__label">Messages</span>
          <span class="unread" hidden></span>
        </button>
        <button id="hangup-btn" class="ctrl ctrl--danger" type="button" title="Raccrocher">
          <svg class="icon" aria-hidden="true"><use href="#i-hangup"/></svg><span class="ctrl__label">Raccrocher</span>
        </button>
      </nav>
    </section>
  </main>

  <!-- Tiroir Chat & Documents -->
  <aside id="chat-drawer" class="drawer" aria-labelledby="chat-title" aria-hidden="true" inert>
    <header class="drawer__header">
      <h2 id="chat-title">Messages &amp; documents</h2>
      <button id="chat-close-btn" class="icon-btn" type="button" aria-label="Fermer le panneau">
        <svg class="icon" aria-hidden="true"><use href="#i-close"/></svg>
      </button>
    </header>
    <p id="chat-empty" class="chat-empty muted">Aucun message pour l'instant. Vous pouvez envoyer un message ou partager une ordonnance, un résultat d'analyse (PDF, JPEG, PNG — 10 Mo max).</p>
    <ol id="chat-list" class="chat-list" aria-live="polite" aria-relevant="additions"></ol>
    <div id="upload-progress" class="upload-progress" hidden>
      <span id="upload-name"></span>
      <progress id="upload-bar" max="100" value="0"></progress>
    </div>
    <form id="chat-form" class="chat-form" autocomplete="off">
      <label class="icon-btn" for="file-input" title="Joindre un document (PDF, JPEG, PNG)">
        <svg class="icon" aria-hidden="true"><use href="#i-attach"/></svg>
        <span class="visually-hidden">Joindre un document</span>
      </label>
      <input id="file-input" type="file" accept="application/pdf,image/jpeg,image/png" class="visually-hidden">
      <label for="chat-input" class="visually-hidden">Votre message</label>
      <textarea id="chat-input" rows="1" placeholder="Écrire un message…"></textarea>
      <button id="chat-send-btn" class="icon-btn icon-btn--primary" type="submit" aria-label="Envoyer">
        <svg class="icon" aria-hidden="true"><use href="#i-send"/></svg>
      </button>
    </form>
  </aside>
  <div id="drawer-backdrop" class="drawer-backdrop" hidden></div>

  <!-- Modale d'appel entrant -->
  <dialog id="incoming-dialog" class="incoming" aria-labelledby="incoming-title" aria-describedby="incoming-from">
    <div class="pulse-avatar" id="incoming-avatar" aria-hidden="true"></div>
    <h2 id="incoming-title">Appel entrant</h2>
    <p id="incoming-from" class="incoming__from"></p>
    <p id="incoming-countdown" class="muted"></p>
    <div class="incoming__actions">
      <button id="reject-btn" class="btn btn--danger btn--lg" type="button">
        <svg class="icon" aria-hidden="true"><use href="#i-hangup"/></svg> Refuser
      </button>
      <button id="accept-btn" class="btn btn--success btn--lg" type="button">
        <svg class="icon" aria-hidden="true"><use href="#i-phone"/></svg> <span id="accept-label">Accepter</span>
      </button>
    </div>
    <button id="accept-audio-btn" class="btn btn--ghost incoming__audio" type="button" hidden>
      <svg class="icon" aria-hidden="true"><use href="#i-phone"/></svg> Répondre en audio uniquement
    </button>
  </dialog>

  <div id="toasts" class="toasts" aria-live="polite" aria-atomic="false"></div>

  <noscript><p class="noscript">La téléconsultation nécessite JavaScript.</p></noscript>
</body>
</html>
