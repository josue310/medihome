/**
 * app.js — Orchestration de la salle de téléconsultation.
 *
 * Parcours : chargement du contexte -> sas matériel -> salle d'attente -> appel.
 * URL : teleconsultation/consultation.jsp?id=<consultationId>[&role=MEDECIN|PATIENT]
 *
 * Règles du backend respectées :
 * - senderId = identité fournie par GET /context (jamais choisie par le client) ;
 * - POST avec l'en-tête X-CSRF-Token (jeton fourni par GET /context) ;
 * - seul l'appelant peut envoyer CALL_CANCEL : il gère le délai de 40 s ;
 *   l'appelé ferme sa modale par CALL_REJECT {reason:'timeout'} en filet de sécurité.
 */
import { SignalingClient } from './signaling-client.js';
import { WebRtcManager } from './webrtc-manager.js';
import {
  MediaDevicesManager, AudioLevelMeter, ScreenWakeLock, describeMediaError, isMediaSupported,
} from './media-devices.js';

const RING_TIMEOUT_MS = 40000;
const CALLEE_SAFETY_TIMEOUT_MS = 45000;
const ROLE_LABEL = { MEDECIN: 'Médecin', PATIENT: 'Patient' };

const $ = (id) => document.getElementById(id);

// ------------------------------------------------------------------ URLs
const params = new URLSearchParams(location.search);
const consultationId = params.get('id');
const requestedRole = params.get('role');
const appRoot = new URL('../', location.href); // racine de l'application (contexte Tomcat)

function apiUrl(path, query = {}) {
  const u = new URL(`api/consultations/${encodeURIComponent(consultationId)}/${path}`, appRoot);
  if (requestedRole) u.searchParams.set('role', requestedRole);
  for (const [k, v] of Object.entries(query)) u.searchParams.set(k, v);
  return u.href;
}

function wsUrl() {
  const u = new URL(`ws/consultation/${encodeURIComponent(consultationId)}`, appRoot);
  u.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  if (requestedRole) u.searchParams.set('role', requestedRole);
  return u.href;
}

// ------------------------------------------------------------------ état
const state = {
  ctx: null,              // { userId, role, displayName, csrfToken, maxUploadBytes, maxMessageLength }
  peer: null,             // { userId, role, displayName }
  peerPresence: 'absent', // absent | present | interrupted
  call: null,             // { phase: outgoing|incoming|active, isCaller, iceServers, rtc, startedAt, timers }
  micOn: true,
  camOn: true,
  audioOnly: false,
  chatOpen: false,
  unread: 0,
  seen: new Set(),
};

const media = new MediaDevicesManager();
const wakeLock = new ScreenWakeLock();
let audioCtx = null;
let meter = null;
let signaling = null;
let ringtone = null;
let clockTimer = 0;

// ------------------------------------------------------------------ utilitaires UI
function showScreen(name) {
  for (const s of ['loading', 'error', 'haircheck', 'waiting', 'call']) {
    $(`screen-${s}`).hidden = s !== name;
  }
}

function toast(message, kind = 'info', ms = 4500) {
  const el = document.createElement('div');
  el.className = `toast toast--${kind}`;
  el.textContent = message;
  $('toasts').appendChild(el);
  setTimeout(() => el.remove(), ms);
}

function showFatal(title, message, actions = []) {
  $('error-title').textContent = title;
  $('error-message').textContent = message;
  const box = $('error-actions');
  box.replaceChildren();
  for (const a of actions) {
    const link = document.createElement('a');
    link.className = `btn ${a.primary ? 'btn--primary' : 'btn--ghost'}`;
    link.href = a.href;
    link.textContent = a.label;
    box.appendChild(link);
  }
  showScreen('error');
}

function setConnectionStatus(kind, text) {
  const el = $('connection-status');
  el.dataset.state = kind;
  el.textContent = text;
}

function initials(name) {
  const parts = String(name || '?').trim().split(/\s+/).filter(Boolean);
  return ((parts[0] || '?')[0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

function formatDuration(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = String(Math.floor((total % 3600) / 60)).padStart(2, '0');
  const s = String(total % 60).padStart(2, '0');
  return h ? `${h}:${m}:${s}` : `${m}:${s}`;
}

function formatBytes(n) {
  if (n < 1024) return `${n} o`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} Ko`;
  return `${(n / (1024 * 1024)).toFixed(1)} Mo`;
}

function peerLabel() {
  if (state.peer && state.peer.displayName) return state.peer.displayName;
  return state.ctx && state.ctx.role === 'MEDECIN' ? 'Votre patient' : 'Votre médecin';
}

// ------------------------------------------------------------------ REST
async function api(path, { method = 'GET', json, retried = false } = {}) {
  const headers = { Accept: 'application/json' };
  const opts = { method, credentials: 'same-origin', headers };
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(json);
  }
  if (method !== 'GET' && state.ctx) headers['X-CSRF-Token'] = state.ctx.csrfToken;
  const res = await fetch(apiUrl(path), opts);
  let body = null;
  try { body = await res.json(); } catch (e) { /* réponse non JSON (ex. 403 CSRF du filtre servlet) */ }
  if (res.status === 403 && method !== 'GET' && !(body && body.error) && !retried) {
    await loadContext(); // jeton CSRF périmé (session renouvelée) : on le rafraîchit une fois
    return api(path, { method, json, retried: true });
  }
  if (!res.ok) {
    const err = new Error((body && body.message) || `Erreur HTTP ${res.status}`);
    err.status = res.status;
    err.code = body && body.error;
    throw err;
  }
  return body;
}

async function loadContext() {
  state.ctx = await api('context');
  return state.ctx;
}

async function fetchIceServers() {
  const res = await api('ice-servers');
  return res.iceServers || [];
}

// ------------------------------------------------------------------ démarrage
async function boot() {
  if (!consultationId || !/^\d+$/.test(consultationId)) {
    showFatal('Lien invalide', "Ce lien de consultation est incomplet. Ouvrez la téléconsultation depuis la liste de vos rendez-vous.",
      [{ label: "Retour à l'accueil", href: new URL('index.jsp', appRoot).href, primary: true }]);
    return;
  }
  if (!('RTCPeerConnection' in window) || !('WebSocket' in window)) {
    showFatal('Navigateur incompatible', 'Utilisez une version récente de Chrome, Edge, Firefox ou Safari.');
    return;
  }
  try {
    await loadContext();
  } catch (err) {
    handleContextError(err);
    return;
  }
  const ctx = state.ctx;
  $('self-name').textContent = ctx.displayName;
  $('self-role').textContent = ROLE_LABEL[ctx.role];
  $('user-chip').hidden = false;
  $('consultation-label').textContent = consultationId;
  $('self-card-name').textContent = `${ctx.displayName} (vous)`;
  $('self-avatar').textContent = initials(ctx.displayName);
  $('chat-input').maxLength = ctx.maxMessageLength;
  updatePeerUi();
  setConnectionStatus('offline', 'Non connecté');

  try {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  } catch (e) {
    audioCtx = null;
  }
  meter = new AudioLevelMeter(audioCtx, renderMeter);
  ringtone = new Ringtone(audioCtx);
  // Les navigateurs exigent un geste utilisateur pour activer le son.
  document.addEventListener('pointerdown', () => audioCtx && audioCtx.resume(), { once: true });

  showScreen('haircheck');
  if (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia) $('screen-btn').hidden = true;
  await initMedia();
}

function handleContextError(err) {
  const login = [
    { label: 'Connexion patient', href: new URL('user_login.jsp', appRoot).href, primary: true },
    { label: 'Connexion médecin', href: new URL('doctor_login.jsp', appRoot).href },
  ];
  if (err.status === 401) {
    showFatal('Connexion requise', 'Connectez-vous à votre espace, puis rouvrez le lien de la téléconsultation.', login);
  } else if (err.status === 403) {
    showFatal('Accès refusé', "Vous n'êtes pas participant(e) de cette consultation.",
      [{ label: "Retour à l'accueil", href: new URL('index.jsp', appRoot).href, primary: true }]);
  } else if (err.code === 'ROLE_REQUIRED') {
    const base = `${location.pathname}?id=${encodeURIComponent(consultationId)}`;
    showFatal('Choisissez votre rôle', 'Vous êtes connecté(e) à la fois comme médecin et comme patient de cette consultation.', [
      { label: 'Entrer comme médecin', href: `${base}&role=MEDECIN`, primary: true },
      { label: 'Entrer comme patient', href: `${base}&role=PATIENT` },
    ]);
  } else {
    showFatal('Service indisponible', `La téléconsultation ne peut pas démarrer pour le moment (${err.message}). Réessayez dans quelques instants.`,
      [{ label: 'Réessayer', href: location.href, primary: true }]);
  }
}

// ------------------------------------------------------------------ étape A : sas matériel
async function initMedia(constraints) {
  $('media-error').hidden = true;
  $('media-warning').hidden = true;
  try {
    await media.open(constraints);
    if (audioCtx) audioCtx.resume().catch(() => {});
    if (media.warning) {
      $('media-warning').textContent = media.warning;
      $('media-warning').hidden = false;
    }
  } catch (err) {
    console.warn('[media]', err);
    if (isMediaSupported()) {
      $('media-error-text').textContent = describeMediaError(err);
    } else {
      showInsecureOriginHelp();
    }
    $('media-error').hidden = false;
  }
  applyLocalTrackStates();
  attachLocalPreviews();
  meter.attach(media.audioTrack);
  await refreshDeviceLists();
  $('join-btn').disabled = false; // on peut rejoindre même sans média (chat, écoute)
}

async function refreshDeviceLists() {
  const { audioInputs, videoInputs } = await media.listDevices();
  fillSelect($('audio-select'), audioInputs, media.audioDeviceId, 'Aucun micro détecté');
  fillSelect($('video-select'), videoInputs, media.videoDeviceId, 'Aucune caméra détectée');
}

function fillSelect(select, devices, currentId, emptyLabel) {
  select.replaceChildren();
  if (!devices.length) {
    select.append(new Option(emptyLabel, ''));
    select.disabled = true;
    return;
  }
  select.disabled = false;
  for (const d of devices) select.append(new Option(d.label, d.id, false, d.id === currentId));
}

function attachLocalPreviews() {
  const stream = media.stream;
  for (const id of ['preview-video', 'waiting-preview']) $(id).srcObject = stream;
  if (state.call && state.call.phase === 'active' && !(state.call.rtc && state.call.rtc.screenTrack)) {
    $('local-video').srcObject = stream;
  }
  const hasVideo = !!media.videoTrack;
  $('preview-placeholder').hidden = hasVideo && state.camOn;
  $('waiting-preview').hidden = !hasVideo || !state.camOn;
}

function applyLocalTrackStates() {
  if (media.audioTrack) media.audioTrack.enabled = state.micOn;
  if (media.videoTrack) media.videoTrack.enabled = state.camOn && !state.audioOnly;
  syncToggle($('preview-mic-btn'), state.micOn, ['Couper le micro', 'Activer le micro'], ['i-mic', 'i-mic-off']);
  syncToggle($('preview-cam-btn'), state.camOn, ['Couper la caméra', 'Activer la caméra'], ['i-cam', 'i-cam-off']);
  $('preview-mic-btn').disabled = !media.audioTrack;
  $('preview-cam-btn').disabled = !media.videoTrack;
}

/** Bouton du sas : aria-pressed = périphérique actif. */
function syncToggle(btn, on, [labelOn, labelOff], [iconOn, iconOff]) {
  btn.setAttribute('aria-pressed', String(on));
  btn.setAttribute('aria-label', on ? labelOn : labelOff);
  btn.querySelector('use').setAttribute('href', `#${on ? iconOn : iconOff}`);
}

function renderMeter(level) {
  $('mic-meter').style.transform = `scaleX(${level.toFixed(3)})`;
  $('mic-meter').parentElement.setAttribute('aria-valuenow', String(Math.round(level * 100)));
}

/**
 * Page ouverte en HTTP via l'IP du poste (pas en localhost) : le navigateur
 * bloque caméra et micro. On explique comment l'autoriser, sans bloquer la page
 * (la salle d'attente et le chat restent utilisables).
 */
function showInsecureOriginHelp() {
  const origin = location.origin;
  const box = $('media-error-text');
  box.replaceChildren(
    document.createTextNode('Le navigateur bloque la caméra et le micro sur cette adresse (' + origin + '). Pour les autoriser :'),
  );
  const list = document.createElement('ol');
  const items = [
    'sur ce PC, ouvrez plutôt http://localhost' + (location.port ? ':' + location.port : '') + location.pathname + location.search + ' ;',
    'sur un autre appareil (Chrome ou Edge) : ouvrez chrome://flags/#unsafely-treat-insecure-origin-as-secure, ajoutez ' + origin + ', choisissez « Enabled », puis relancez le navigateur.',
  ];
  for (const text of items) {
    const li = document.createElement('li');
    li.textContent = text;
    list.appendChild(li);
  }
  box.appendChild(list);
  $('retry-media-btn').hidden = true;
}

// ------------------------------------------------------------------ étape B : salle d'attente
function joinWaitingRoom() {
  if (audioCtx) audioCtx.resume().catch(() => {});
  $('join-btn').disabled = true;
  signaling = new SignalingClient({ url: wsUrl(), consultationId, senderId: state.ctx.userId });
  wireSignaling(signaling);
  setConnectionStatus('reconnecting', 'Connexion…');
  signaling.connect();
  showScreen('waiting');
  attachLocalPreviews();
  updateWaitingUi();
}

function updatePeerUi() {
  const name = peerLabel();
  $('peer-name').textContent = name;
  $('peer-avatar').textContent = state.peer ? initials(name) : '?';
  for (const id of ['outgoing-avatar', 'incoming-avatar', 'remote-placeholder-avatar', 'audio-only-avatar']) {
    $(id).textContent = initials(name);
  }
  $('call-peer-name').textContent = name;
  $('audio-only-name').textContent = name;
  const card = $('peer-card');
  card.dataset.present = state.peerPresence === 'present' ? 'true'
    : state.peerPresence === 'interrupted' ? 'interrupted' : 'false';
  const status = $('peer-status');
  const dot = document.createElement('span');
  const texts = {
    present: ['dot dot--ok', "Présent(e) en salle d'attente"],
    interrupted: ['dot dot--warn', 'Connexion interrompue, reconnexion en cours…'],
    absent: ['dot', 'Pas encore connecté(e)'],
  };
  const [cls, text] = texts[state.peerPresence];
  dot.className = cls;
  status.replaceChildren(dot, document.createTextNode(text));
}

function updateWaitingUi() {
  const canCall = !!signaling && signaling.connected && state.peerPresence === 'present' && !state.call;
  $('call-btn').disabled = !canCall;
  $('audio-call-btn').disabled = !canCall;
  updatePeerUi();
}

// ------------------------------------------------------------------ signalisation
function wireSignaling(sc) {
  sc.addEventListener('open', (e) => {
    setConnectionStatus('online', 'Connecté');
    hideBanner('signaling');
    if (e.detail.reconnected) {
      toast('Connexion au serveur rétablie.', 'success', 2500);
      if (state.call && state.call.rtc) state.call.rtc.onSignalingReconnected();
    }
    updateWaitingUi();
  });
  sc.addEventListener('close', (e) => {
    if (!e.detail.willReconnect) {
      setConnectionStatus('offline', 'Déconnecté');
      return;
    }
    setConnectionStatus('reconnecting', 'Reconnexion…');
    if (state.call && state.call.phase === 'active') {
      showBanner('signaling', 'Connexion au serveur perdue — reconnexion automatique…');
    }
    updateWaitingUi();
    console.info(`[signaling] fermée (${e.detail.code}), nouvel essai dans ${e.detail.retryInMs} ms`);
  });
  sc.addEventListener('fatal', (e) => {
    setConnectionStatus('offline', 'Déconnecté');
    endCallLocally(null);
    if (e.detail.code === 4001) {
      showFatal('Consultation ouverte ailleurs', 'Cette consultation a été ouverte dans un autre onglet ou sur un autre appareil. Cette fenêtre a été déconnectée.',
        [{ label: 'Reprendre ici', href: location.href, primary: true }]);
    } else {
      showFatal('Session terminée', "Votre session a expiré ou vous vous êtes déconnecté(e). Reconnectez-vous pour reprendre la consultation.",
        [{ label: 'Se reconnecter', href: new URL(state.ctx.role === 'MEDECIN' ? 'doctor_login.jsp' : 'user_login.jsp', appRoot).href, primary: true }]);
    }
  });

  sc.addEventListener('ROOM_STATE', (e) => onRoomState(e.detail.payload));
  sc.addEventListener('PARTICIPANT_JOINED', (e) => onParticipantJoined(e.detail.payload));
  sc.addEventListener('PARTICIPANT_LEFT', (e) => onParticipantLeft(e.detail.payload));
  sc.addEventListener('CALL_REQUEST', (e) => onIncomingCall(e.detail));
  sc.addEventListener('CALL_ACCEPT', (e) => onCallAccepted(e.detail));
  sc.addEventListener('CALL_REJECT', (e) => onCallRejected(e.detail.payload));
  sc.addEventListener('CALL_CANCEL', (e) => onCallCancelled(e.detail.payload));
  sc.addEventListener('BUSY', () => onBusy());
  sc.addEventListener('HANGUP', () => endCallLocally('Votre correspondant a raccroché.'));
  for (const t of ['SDP_OFFER', 'SDP_ANSWER', 'ICE_CANDIDATE', 'ICE_RESTART']) {
    sc.addEventListener(t, (e) => {
      if (state.call && state.call.rtc) state.call.rtc.handleSignal(e.detail);
    });
  }
  sc.addEventListener('CHAT_NOTIFY', (e) => onChatNotify(e.detail));
  sc.addEventListener('ERROR', (e) => onServerError(e.detail.payload));
}

function setPeer(info) {
  if (info) state.peer = { userId: info.userId, role: info.role, displayName: info.displayName };
}

/** État initial envoyé à chaque (re)connexion : réconcilie l'appel local avec le serveur. */
function onRoomState(p) {
  setPeer(p.peer);
  if (p.peer) state.peerPresence = 'present';
  else if (state.peerPresence !== 'interrupted') state.peerPresence = 'absent';
  const serverCall = p.call;
  const local = state.call;

  if (local && local.phase === 'active') {
    if (serverCall && serverCall.state === 'IN_CALL') {
      hideBanner('signaling'); // appel repris après la coupure
    } else {
      endCallLocally("L'appel a été interrompu pendant la coupure réseau.");
    }
  } else if (local && !serverCall) {
    endCallLocally(local.phase === 'incoming' ? null : 'Appel interrompu.');
  } else if (!local && serverCall) {
    if (serverCall.state === 'IN_CALL') {
      // Page rechargée en plein appel : le flux WebRTC est perdu, on clôt proprement.
      signaling.send('HANGUP', { reason: 'page-reloaded' });
      toast("L'appel précédent a été interrompu (page rechargée).", 'warning');
    } else if (serverCall.calleeId === state.ctx.userId) {
      onIncomingCall({ senderId: serverCall.callerId, payload: {} });
    } else {
      signaling.send('CALL_CANCEL', { reason: 'page-reloaded' });
    }
  }
  updateWaitingUi();
}

function onParticipantJoined(p) {
  setPeer(p);
  const wasInterrupted = state.peerPresence === 'interrupted';
  state.peerPresence = 'present';
  if (p.resumed) {
    hideBanner('peer');
    toast(`${peerLabel()} est de nouveau connecté(e).`, 'success', 2500);
  } else if (!wasInterrupted) {
    toast(`${peerLabel()} a rejoint la salle d'attente.`, 'success');
  }
  updateWaitingUi();
}

function onParticipantLeft(p) {
  if (p.reason === 'CONNECTION_INTERRUPTED') {
    state.peerPresence = 'interrupted';
    showBanner('peer', `Connexion de ${peerLabel()} interrompue — en attente de sa reconnexion…`);
  } else {
    state.peerPresence = 'absent';
    hideBanner('peer');
    if (state.call) endCallLocally(`${peerLabel()} a quitté la consultation.`);
    else toast(`${peerLabel()} a quitté la salle d'attente.`);
  }
  updateWaitingUi();
}

function onServerError(p) {
  console.warn('[serveur]', p.code, p.message);
  if (p.code === 'PEER_NOT_CONNECTED') toast(`${peerLabel()} n'est pas en salle d'attente.`, 'warning');
  else if (p.code === 'PEER_RECONNECTING') showBanner('peer', `${peerLabel()} se reconnecte…`);
}

// ------------------------------------------------------------------ cycle d'appel
/**
 * Démarre un appel. kind = 'video' (caméra + micro) ou 'audio' (micro seul :
 * la caméra est libérée, son voyant s'éteint ; la vidéo pourra être activée
 * plus tard pendant l'appel).
 */
async function startCall(kind = 'video') {
  if (state.call || state.peerPresence !== 'present') return;
  const call = { phase: 'outgoing', isCaller: true, timers: [], media: kind };
  state.call = call;
  updateWaitingUi();
  if (kind === 'video' && !(await ensureCamera())) call.media = 'audio';
  if (state.call !== call) return;
  state.audioOnly = call.media === 'audio';
  call.startedAsAudio = state.audioOnly;
  $('outgoing-panel').hidden = false;
  $('outgoing-title').textContent = call.media === 'audio' ? 'Appel audio en cours…' : 'Appel vidéo en cours…';
  $('outgoing-countdown').textContent = `Appel de ${peerLabel()}…`;
  ringtone.start('outgoing');
  try {
    call.iceServers = await fetchIceServers(); // serveurs STUN/TURN récupérés AVANT l'appel
  } catch (err) {
    resetPendingCall();
    toast(`Impossible de préparer l'appel : ${err.message}`, 'error');
    return;
  }
  if (state.call !== call) return; // annulé entre-temps
  if (!signaling.send('CALL_REQUEST', { media: call.media })) {
    resetPendingCall();
    toast('Connexion au serveur indisponible, réessayez.', 'error');
    return;
  }
  const deadline = Date.now() + RING_TIMEOUT_MS;
  call.timers.push(setInterval(() => {
    const left = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
    $('outgoing-countdown').textContent = `Appel de ${peerLabel()}… (${left} s)`;
  }, 1000));
  call.timers.push(setTimeout(() => {
    if (state.call !== call || call.phase !== 'outgoing') return;
    signaling.send('CALL_CANCEL', { reason: 'timeout' });
    resetPendingCall();
    toast(`${peerLabel()} n'a pas répondu.`, 'warning');
  }, RING_TIMEOUT_MS));
}

function cancelOutgoingCall() {
  if (!state.call || state.call.phase !== 'outgoing') return;
  signaling.send('CALL_CANCEL', { reason: 'cancelled' });
  resetPendingCall();
}

function onIncomingCall(msg) {
  if (state.call) return; // le serveur répond BUSY à l'appelant
  const audioCall = !!(msg.payload && msg.payload.media === 'audio');
  const call = { phase: 'incoming', isCaller: false, timers: [], media: audioCall ? 'audio' : 'video' };
  state.call = call;
  call.icePromise = fetchIceServers(); // préchargé pendant la sonnerie
  call.icePromise.catch(() => {});
  $('incoming-title').textContent = audioCall ? 'Appel audio entrant' : 'Appel vidéo entrant';
  $('incoming-from').textContent = `${peerLabel()} vous appelle`;
  // Appel vidéo : on peut aussi répondre en audio uniquement (caméra non utilisée).
    $('accept-audio-btn').hidden = audioCall;
  const dialog = $('incoming-dialog');
  if (!dialog.open) dialog.showModal();
  $('accept-btn').focus();
  ringtone.start('incoming');
  if (navigator.vibrate) navigator.vibrate([400, 200, 400]);
  flashTitle('📞 Appel entrant');
  const deadline = Date.now() + RING_TIMEOUT_MS;
  const tick = () => {
    const left = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
    $('incoming-countdown').textContent = `Sans réponse, l'appel s'arrêtera dans ${left} s`;
  };
  tick();
  call.timers.push(setInterval(tick, 1000));
  // L'appelant annule à 40 s (CALL_CANCEL) ; filet de sécurité si son message n'arrive pas.
  call.timers.push(setTimeout(() => {
    if (state.call !== call || call.phase !== 'incoming') return;
    signaling.send('CALL_REJECT', { reason: 'timeout' });
    resetPendingCall();
    toast('Appel manqué.', 'warning');
  }, CALLEE_SAFETY_TIMEOUT_MS));
  updateWaitingUi();
}

/** Décroche. asAudio = true : répondre en audio uniquement à un appel vidéo. */
async function acceptCall(asAudio = false) {
  const call = state.call;
  if (!call || call.phase !== 'incoming') return;
  clearCallTimers(call);
  ringtone.stop();
  stopFlashTitle();
  $('incoming-dialog').close();
  if (asAudio) call.media = 'audio';
  if (call.media === 'video' && !(await ensureCamera())) call.media = 'audio';
  if (state.call !== call) return;
  state.audioOnly = call.media === 'audio';
  call.startedAsAudio = state.audioOnly;
  let iceServers;
  try {
    iceServers = await call.icePromise;
  } catch (err) {
    signaling.send('CALL_REJECT', { reason: 'ice-servers-unavailable' });
    resetPendingCall();
    toast("Impossible d'établir l'appel (serveurs réseau indisponibles).", 'error');
    return;
  }
  if (state.call !== call) return;
  await startRtc(call, false, iceServers); // prêt à recevoir l'offre AVANT d'accepter
  signaling.send('CALL_ACCEPT', { media: call.media });
  enterActiveCall(call);
}

/**
 * S'assure qu'une caméra est disponible pour un appel vidéo (elle a pu être
 * libérée par un appel audio précédent). Retourne false si aucune caméra n'est
 * utilisable : l'appel se fera alors en audio.
 */
async function ensureCamera() {
  if (media.videoTrack) return true;
  try {
    const track = await media.reacquire('video');
    track.enabled = state.camOn;
    attachLocalPreviews();
    applyLocalTrackStates();
    return true;
  } catch (err) {
    toast(`Caméra indisponible, l'appel se fera en audio. ${describeMediaError(err)}`, 'warning', 6000);
    return false;
  }
}

function rejectCall() {
  if (!state.call || state.call.phase !== 'incoming') return;
  signaling.send('CALL_REJECT', { reason: 'declined' });
  resetPendingCall();
}

async function onCallAccepted(msg) {
  const call = state.call;
  if (!call || call.phase !== 'outgoing') return;
  clearCallTimers(call);
  ringtone.stop();
  if (call.media === 'video' && msg && msg.payload && msg.payload.media === 'audio') {
    toast(`${peerLabel()} a répondu en audio uniquement.`, 'info', 4000);
  }
  await startRtc(call, true, call.iceServers); // l'appelant crée l'offre
  enterActiveCall(call);
}

function onCallRejected(p) {
  if (!state.call || state.call.phase !== 'outgoing') return;
  resetPendingCall();
  toast(p && p.reason === 'timeout' ? `${peerLabel()} n'a pas répondu.` : `${peerLabel()} a refusé l'appel.`, 'warning');
}

function onCallCancelled() {
  if (!state.call || state.call.phase !== 'incoming') return;
  resetPendingCall();
  toast(`Appel manqué de ${peerLabel()}.`, 'warning');
}

function onBusy() {
  if (!state.call || state.call.phase !== 'outgoing') return;
  resetPendingCall();
  toast(`${peerLabel()} est déjà en communication.`, 'warning');
}

function clearCallTimers(call) {
  for (const t of call.timers) { clearTimeout(t); clearInterval(t); }
  call.timers = [];
}

/** Fin d'un appel non établi (sonnerie). */
function resetPendingCall() {
  if (state.call) clearCallTimers(state.call);
  state.call = null;
  ringtone.stop();
  stopFlashTitle();
  if ($('incoming-dialog').open) $('incoming-dialog').close();
  $('outgoing-panel').hidden = true;
  updateWaitingUi();
}

// ------------------------------------------------------------------ appel actif
async function startRtc(call, isOfferer, iceServers) {
  // Appel audio : caméra libérée avant tout ; une piste vidéo reste négociée
  // (sans image) pour pouvoir activer la vidéo plus tard sans renégociation.
  if (state.audioOnly) media.releaseVideo();
  const rtc = new WebRtcManager({ signaling, iceServers, localStream: media.stream, isOfferer });
  call.rtc = rtc;
  rtc.setMicEnabled(state.micOn);
  rtc.setCameraEnabled(state.camOn);

  let remoteVideoTrack = null;
  let remoteVideoActive = true;
  const updateRemotePlaceholder = () => {
    $('remote-placeholder').hidden = !!remoteVideoTrack && !remoteVideoTrack.muted && remoteVideoActive;
  };
  rtc.addEventListener('remotetrack', (e) => {
    const video = $('remote-video');
    if (video.srcObject !== e.detail.stream) video.srcObject = e.detail.stream;
    if (e.detail.track.kind === 'video') {
      remoteVideoTrack = e.detail.track;
      remoteVideoTrack.addEventListener('mute', updateRemotePlaceholder);
      remoteVideoTrack.addEventListener('unmute', updateRemotePlaceholder);
      updateRemotePlaceholder();
    }
  });
  // Correspondant en audio seul (plus aucun paquet vidéo) : image figée remplacée par un avatar.
  rtc.addEventListener('remotevideo', (e) => {
    remoteVideoActive = e.detail.active;
    updateRemotePlaceholder();
    // Appel audio : le correspondant vient d'activer sa caméra.
    if (e.detail.active && state.audioOnly && state.call && state.call.rtc === rtc
      && state.call.startedAsAudio && !state.call.peerVideoNotified) {
      state.call.peerVideoNotified = true;
      toast(`${peerLabel()} a activé sa caméra. Touchez « Vidéo » pour la voir et activer la vôtre.`, 'info', 6000);
    }
  });
  rtc.addEventListener('connectionstate', (e) => {
    const s = e.detail.state;
    if (s === 'connected' || s === 'completed') hideBanner('ice');
    else if (s === 'disconnected') showBanner('ice', 'Connexion instable…');
    else if (s === 'failed') showBanner('ice', 'Connexion perdue — rétablissement en cours…');
  });
  rtc.addEventListener('icerestart', () => showBanner('ice', 'Rétablissement de la connexion (changement de réseau)…'));
  rtc.addEventListener('quality', (e) => renderQuality(e.detail));
  rtc.addEventListener('degraded', () => { if (!state.audioOnly) $('degraded-hint').hidden = false; });
  rtc.addEventListener('recovered', () => { $('degraded-hint').hidden = true; });
  rtc.addEventListener('screenshare', (e) => renderScreenShare(e.detail));
  rtc.addEventListener('error', (e) => console.error('[webrtc]', e.detail.message));

  await rtc.start();
  await rtc.setAudioOnly(state.audioOnly);
}

function enterActiveCall(call) {
  call.phase = 'active';
  call.startedAt = Date.now();
  $('outgoing-panel').hidden = true;
  showScreen('call');
  $('local-video').srcObject = media.stream;
  $('network-quality').hidden = false;
  renderQuality(null);
  renderCallControls();
  updatePeerUi();
  clearInterval(clockTimer);
  const tick = () => {
    const text = formatDuration(Date.now() - call.startedAt);
    $('call-timer').textContent = text;
    $('audio-only-timer').textContent = text;
  };
  tick();
  clockTimer = setInterval(tick, 1000);
  wakeLock.enable(); // empêche la mise en veille de l'écran pendant l'appel
}

function hangUp() {
  if (!state.call) return;
  signaling.send('HANGUP', {});
  endCallLocally('Appel terminé.');
}

/** Termine localement tout appel (établi ou non) et revient à la salle d'attente. */
function endCallLocally(message) {
  const call = state.call;
  if (!call) return;
  clearCallTimers(call);
  if (call.rtc) call.rtc.close();
  state.call = null;
  state.audioOnly = false; // le type d'appel se choisit à chaque appel
  ringtone.stop();
  stopFlashTitle();
  clearInterval(clockTimer);
  wakeLock.disable();
  if ($('incoming-dialog').open) $('incoming-dialog').close();
  $('outgoing-panel').hidden = true;
  $('remote-video').srcObject = null;
  $('network-quality').hidden = true;
  $('degraded-hint').hidden = true;
  hideBanner('ice');
  hideBanner('signaling');
  hideBanner('peer');
  $('local-pip').querySelector('video').classList.add('mirror');
  if (!$('screen-call').hidden) showScreen('waiting');
  attachLocalPreviews();
  updateWaitingUi();
  if (message) toast(message);
}

// --- contrôles
function toggleMic() {
  state.micOn = !state.micOn;
  if (media.audioTrack) media.audioTrack.enabled = state.micOn;
  if (state.call && state.call.rtc) state.call.rtc.setMicEnabled(state.micOn);
  applyLocalTrackStates();
  renderCallControls();
}

function toggleCam() {
  state.camOn = !state.camOn;
  if (state.call && state.call.rtc) state.call.rtc.setCameraEnabled(state.camOn);
  else if (media.videoTrack) media.videoTrack.enabled = state.camOn;
  applyLocalTrackStates();
  attachLocalPreviews();
  renderCallControls();
}

/**
 * Bascule audio seul <-> vidéo pendant l'appel, sans renégociation :
 * - vers l'audio : plus aucune vidéo envoyée (replaceTrack(null)) et caméra libérée ;
 * - vers la vidéo : la caméra est récupérée puis réinjectée (replaceTrack(caméra)).
 */
async function setAudioOnly(on) {
  const rtc = state.call && state.call.rtc;
  $('degraded-hint').hidden = true;
  if (!on && rtc && !media.videoTrack) {
    if (!(await ensureCamera())) return; // pas de caméra : on reste en audio
    await rtc.replaceLocalTrack('video', media.videoTrack);
  }
  state.audioOnly = on;
  if (state.call) state.call.media = on ? 'audio' : 'video';
  if (rtc) {
    await rtc.setAudioOnly(on);
    if (on) media.releaseVideo(); // voyant de la caméra éteint pendant l'audio seul
  } else if (media.videoTrack) {
    media.videoTrack.enabled = !on && state.camOn;
  }
  attachLocalPreviews();
  renderCallControls();
}

async function toggleScreenShare() {
  const rtc = state.call && state.call.rtc;
  if (!rtc) return;
  try {
    if (rtc.screenTrack) await rtc.stopScreenShare();
    else await rtc.startScreenShare();
  } catch (err) {
    if (err.name !== 'NotAllowedError' && err.name !== 'AbortError') {
      toast(`Partage d'écran impossible : ${describeMediaError(err)}`, 'error');
    }
  }
}

function renderScreenShare({ active, track }) {
  const pipVideo = $('local-video');
  pipVideo.srcObject = active ? new MediaStream([track]) : media.stream;
  pipVideo.classList.toggle('mirror', !active); // un écran partagé ne doit pas être inversé
  $('pip-label').textContent = active ? 'Votre écran' : 'Vous';
  renderCallControls();
}

function renderCallControls() {
  const rtc = state.call && state.call.rtc;
  const sharing = !!(rtc && rtc.screenTrack);
  setCtrl($('mic-btn'), !state.micOn, state.micOn ? 'Couper le micro' : 'Réactiver le micro', state.micOn ? 'i-mic' : 'i-mic-off', true);
  setCtrl($('cam-btn'), !state.camOn, state.camOn ? 'Couper la caméra' : 'Réactiver la caméra', state.camOn ? 'i-cam' : 'i-cam-off', true);
  setCtrl($('audio-only-btn'), state.audioOnly, state.audioOnly ? 'Activer la vidéo' : 'Passer en audio seul', state.audioOnly ? 'i-cam' : 'i-audio', false);
  $('audio-only-label').textContent = state.audioOnly ? 'Vidéo' : 'Audio seul';
  $('audio-only-hint').textContent = state.call && state.call.startedAsAudio
    ? 'Appel audio' : 'Vidéo désactivée pour économiser la connexion';
  setCtrl($('screen-btn'), sharing, sharing ? 'Arrêter le partage' : "Partager l'écran", 'i-screen', false);
  $('mic-btn').disabled = !media.audioTrack;
  $('cam-btn').disabled = !media.videoTrack || state.audioOnly;
  $('screen-btn').disabled = state.audioOnly;
  $('audio-only-view').hidden = !state.audioOnly;
  $('local-pip').hidden = state.audioOnly;
  $('local-pip').dataset.camera = sharing || (state.camOn && media.videoTrack) ? 'on' : 'off';
}

function setCtrl(btn, pressed, title, icon, isMute) {
  btn.setAttribute('aria-pressed', String(pressed));
  btn.title = title;
  btn.setAttribute('aria-label', title);
  btn.classList.toggle('ctrl--mute-active', isMute);
  btn.querySelector('use').setAttribute('href', `#${icon}`);
}

function renderQuality(q) {
  const el = $('network-quality');
  const level = q ? q.level : 'unknown';
  el.dataset.level = level;
  const label = { good: 'Bonne', medium: 'Moyenne', poor: 'Faible', unknown: 'En cours de mesure' }[level];
  const detail = q ? ` — pertes ${q.lossPct.toFixed(1)} %, gigue ${Math.round(q.jitterMs)} ms${q.rttMs != null ? `, latence ${Math.round(q.rttMs)} ms` : ''}` : '';
  el.setAttribute('aria-label', `Qualité réseau : ${label}`);
  el.title = `Qualité réseau : ${label}${detail}`;
}

// --- bannières d'état (une par source, la plus récente affichée)
const banners = new Map();
function showBanner(source, text) {
  banners.set(source, text);
  renderBanner();
}
function hideBanner(source) {
  banners.delete(source);
  renderBanner();
}
function renderBanner() {
  const el = $('call-banner');
  const texts = [...banners.values()];
  el.hidden = texts.length === 0;
  el.textContent = texts[texts.length - 1] || '';
}

// --- vignette locale déplaçable
function makeDraggable(el, container) {
  let startX = 0;
  let startY = 0;
  let originLeft = 0;
  let originTop = 0;
  el.addEventListener('pointerdown', (e) => {
    el.setPointerCapture(e.pointerId);
    el.classList.add('dragging');
    const r = el.getBoundingClientRect();
    const c = container.getBoundingClientRect();
    startX = e.clientX;
    startY = e.clientY;
    originLeft = r.left - c.left;
    originTop = r.top - c.top;
  });
  el.addEventListener('pointermove', (e) => {
    if (!el.hasPointerCapture(e.pointerId)) return;
    place(originLeft + e.clientX - startX, originTop + e.clientY - startY);
  });
  const end = (e) => {
    if (el.hasPointerCapture(e.pointerId)) el.releasePointerCapture(e.pointerId);
    el.classList.remove('dragging');
  };
  el.addEventListener('pointerup', end);
  el.addEventListener('pointercancel', end);
  // Déplacement au clavier (accessibilité) : flèches.
  el.addEventListener('keydown', (e) => {
    const step = e.shiftKey ? 40 : 12;
    const d = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }[e.key];
    if (!d) return;
    e.preventDefault();
    const r = el.getBoundingClientRect();
    const c = container.getBoundingClientRect();
    place(r.left - c.left + d[0], r.top - c.top + d[1]);
  });
  function place(left, top) {
    const c = container.getBoundingClientRect();
    const maxLeft = c.width - el.offsetWidth - 8;
    const maxTop = c.height - el.offsetHeight - 8;
    el.style.left = `${Math.min(Math.max(8, left), maxLeft)}px`;
    el.style.top = `${Math.min(Math.max(8, top), maxTop)}px`;
    el.style.right = 'auto';
    el.style.bottom = 'auto';
  }
}

// ------------------------------------------------------------------ chat & documents
function openDrawer() {
  state.chatOpen = true;
  const d = $('chat-drawer');
  d.classList.add('open');
  d.setAttribute('aria-hidden', 'false');
  d.inert = false;
  $('drawer-backdrop').hidden = false;
  state.unread = 0;
  renderUnread();
  setTimeout(() => $('chat-input').focus(), 250);
}

function closeDrawer() {
  state.chatOpen = false;
  const d = $('chat-drawer');
  d.classList.remove('open');
  d.setAttribute('aria-hidden', 'true');
  d.inert = true;
  $('drawer-backdrop').hidden = true;
}

function toggleDrawer() {
  if (state.chatOpen) closeDrawer(); else openDrawer();
}

function renderUnread() {
  for (const badge of document.querySelectorAll('.unread')) {
    badge.hidden = state.unread === 0;
    badge.textContent = state.unread > 9 ? '9+' : String(state.unread);
  }
}

function onChatNotify(msg) {
  const p = msg.payload || {};
  if (p.kind === 'MESSAGE') {
    addChatItem({ kind: 'MESSAGE', id: p.messageId, senderId: msg.senderId, content: p.content, createdAt: p.createdAt });
  } else if (p.kind === 'ATTACHMENT') {
    addChatItem({
      kind: 'ATTACHMENT', id: p.attachmentId, senderId: msg.senderId, fileName: p.fileName,
      mimeType: p.mimeType, sizeBytes: p.sizeBytes, createdAt: p.createdAt,
    });
  }
}

/** Ajoute un message ou un document (dédoublonné : la réponse du POST et CHAT_NOTIFY se croisent). */
function addChatItem(item) {
  const key = `${item.kind}:${item.id}`;
  if (state.seen.has(key)) return;
  state.seen.add(key);
  const mine = item.senderId === state.ctx.userId;

  const li = document.createElement('li');
  li.className = `msg${mine ? ' msg--mine' : ''}`;
  if (item.kind === 'MESSAGE') {
    const bubble = document.createElement('div');
    bubble.className = 'msg__bubble';
    bubble.textContent = item.content; // textContent : jamais interprété comme du HTML
    li.appendChild(bubble);
  } else {
    li.appendChild(buildFileCard(item));
  }
  const meta = document.createElement('div');
  meta.className = 'msg__meta';
  const time = new Date(item.createdAt || Date.now()).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  meta.textContent = `${mine ? 'Vous' : peerLabel()} · ${time}`;
  li.appendChild(meta);

  $('chat-list').appendChild(li);
  $('chat-empty').hidden = true;
  li.scrollIntoView({ block: 'end', behavior: 'smooth' });
  if (!mine && !state.chatOpen) {
    state.unread++;
    renderUnread();
    toast(item.kind === 'MESSAGE' ? `Nouveau message de ${peerLabel()}` : `${peerLabel()} a partagé un document`, 'info', 3000);
  }
}

function buildFileCard(item) {
  const card = document.createElement('div');
  card.className = 'file-card';
  const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  icon.setAttribute('class', 'icon file-card__icon');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', '#i-file');
  icon.appendChild(use);
  const info = document.createElement('div');
  const name = document.createElement('div');
  name.className = 'file-card__name';
  name.textContent = item.fileName;
  const size = document.createElement('div');
  size.className = 'file-card__size';
  size.textContent = `${item.mimeType === 'application/pdf' ? 'PDF' : 'Image'} · ${formatBytes(item.sizeBytes || 0)}`;
  info.append(name, size);
  card.append(icon, info);

  const inlineUrl = apiUrl(`attachments/${encodeURIComponent(item.id)}`, { disposition: 'inline' });
  if (item.mimeType && item.mimeType.startsWith('image/')) {
    const img = document.createElement('img');
    img.className = 'file-card__thumb';
    img.alt = `Aperçu de ${item.fileName}`;
    img.loading = 'lazy';
    img.src = inlineUrl;
    card.appendChild(img);
  }
  const actions = document.createElement('div');
  actions.className = 'file-card__actions';
  const open = document.createElement('a');
  open.href = inlineUrl;
  open.target = '_blank';
  open.rel = 'noopener noreferrer';
  open.textContent = 'Ouvrir';
  const dl = document.createElement('a');
  dl.href = apiUrl(`attachments/${encodeURIComponent(item.id)}`);
  dl.setAttribute('download', item.fileName);
  dl.appendChild(svgIcon('i-download'));
  dl.appendChild(document.createTextNode('Télécharger'));
  actions.append(open, dl);
  card.appendChild(actions);
  return card;
}

function svgIcon(id) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'icon');
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', `#${id}`);
  svg.appendChild(use);
  return svg;
}

async function sendChatMessage() {
  const input = $('chat-input');
  const content = input.value.trim();
  if (!content) return;
  $('chat-send-btn').disabled = true;
  try {
    const saved = await api('messages', { method: 'POST', json: { content } });
    addChatItem({ kind: 'MESSAGE', id: saved.id, senderId: saved.senderId, content: saved.content, createdAt: saved.createdAt });
    input.value = '';
    autosize(input);
  } catch (err) {
    toast(`Message non envoyé : ${err.message}`, 'error');
  } finally {
    $('chat-send-btn').disabled = false;
    input.focus();
  }
}

const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];

function uploadFile(file) {
  if (!ALLOWED_TYPES.includes(file.type)) {
    toast('Format non accepté : envoyez un PDF, un JPEG ou un PNG.', 'error');
    return;
  }
  if (file.size > state.ctx.maxUploadBytes) {
    toast(`Fichier trop volumineux (maximum ${formatBytes(state.ctx.maxUploadBytes)}).`, 'error');
    return;
  }
  // XMLHttpRequest plutôt que fetch : suivi de la progression de l'envoi.
  const xhr = new XMLHttpRequest();
  const form = new FormData();
  form.append('file', file, file.name);
  $('upload-name').textContent = `Envoi de ${file.name}…`;
  $('upload-bar').value = 0;
  $('upload-progress').hidden = false;
  xhr.upload.addEventListener('progress', (e) => {
    if (e.lengthComputable) $('upload-bar').value = Math.round((100 * e.loaded) / e.total);
  });
  xhr.addEventListener('load', () => {
    $('upload-progress').hidden = true;
    let body = null;
    try { body = JSON.parse(xhr.responseText); } catch (e) { /* non JSON */ }
    if (xhr.status === 201 && body) {
      addChatItem({
        kind: 'ATTACHMENT', id: body.id, senderId: body.senderId, fileName: body.fileName,
        mimeType: body.mimeType, sizeBytes: body.sizeBytes, createdAt: body.createdAt,
      });
      toast('Document partagé.', 'success', 2500);
    } else {
      toast(`Document refusé : ${(body && body.message) || `erreur ${xhr.status}`}`, 'error');
    }
  });
  xhr.addEventListener('error', () => {
    $('upload-progress').hidden = true;
    toast("Échec de l'envoi du document (réseau).", 'error');
  });
  xhr.open('POST', apiUrl('attachments'));
  xhr.setRequestHeader('X-CSRF-Token', state.ctx.csrfToken);
  xhr.setRequestHeader('Accept', 'application/json');
  xhr.send(form);
}

function autosize(textarea) {
  textarea.style.height = 'auto';
  textarea.style.height = `${Math.min(textarea.scrollHeight, 128)}px`;
}

// ------------------------------------------------------------------ sonneries (Web Audio, aucun fichier)
class Ringtone {
  constructor(ctx) {
    this.ctx = ctx;
    this.timer = 0;
    this.nodes = [];
  }

  /** 'incoming' : double bip répété ; 'outgoing' : tonalité de retour d'appel (425 Hz, 1 s / 3 s). */
  start(kind) {
    this.stop();
    if (!this.ctx) return;
    this.ctx.resume().catch(() => {});
    const play = () => {
      const t = this.ctx.currentTime;
      if (kind === 'incoming') {
        this._tone([480, 620], t, 0.35);
        this._tone([480, 620], t + 0.5, 0.35);
      } else {
        this._tone([425], t, 1.0, 0.06);
      }
    };
    play();
    this.timer = setInterval(play, kind === 'incoming' ? 2000 : 4000);
  }

  _tone(freqs, at, duration, volume = 0.12) {
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0, at);
    gain.gain.linearRampToValueAtTime(volume, at + 0.02);
    gain.gain.setValueAtTime(volume, at + duration - 0.03);
    gain.gain.linearRampToValueAtTime(0, at + duration);
    gain.connect(this.ctx.destination);
    for (const f of freqs) {
      const osc = this.ctx.createOscillator();
      osc.frequency.value = f;
      osc.connect(gain);
      osc.start(at);
      osc.stop(at + duration);
      this.nodes.push(osc);
      osc.onended = () => { this.nodes = this.nodes.filter(n => n !== osc); };
    }
  }

  stop() {
    clearInterval(this.timer);
    this.timer = 0;
    for (const n of this.nodes) { try { n.stop(); } catch (e) { /* déjà arrêté */ } }
    this.nodes = [];
  }
}

// --- titre clignotant (onglet en arrière-plan)
let titleTimer = 0;
const baseTitle = document.title;
function flashTitle(text) {
  stopFlashTitle();
  let on = false;
  titleTimer = setInterval(() => { on = !on; document.title = on ? text : baseTitle; }, 1000);
}
function stopFlashTitle() {
  clearInterval(titleTimer);
  document.title = baseTitle;
}

// ------------------------------------------------------------------ liaisons DOM
function bindUi() {
  $('join-btn').addEventListener('click', joinWaitingRoom);
  $('retry-media-btn').addEventListener('click', () => initMedia());
  $('audio-select').addEventListener('change', (e) => initMedia({ audioDeviceId: e.target.value || null }));
  $('video-select').addEventListener('change', (e) => initMedia({ videoDeviceId: e.target.value || null }));
  $('preview-mic-btn').addEventListener('click', toggleMic);
  $('preview-cam-btn').addEventListener('click', toggleCam);
  media.addEventListener('devicechange', () => { if (!$('screen-haircheck').hidden) refreshDeviceLists(); });
  media.addEventListener('trackended', (e) => recoverDevice(e.detail.kind));

  $('call-btn').addEventListener('click', () => startCall('video'));
  $('audio-call-btn').addEventListener('click', () => startCall('audio'));
  $('accept-audio-btn').addEventListener('click', () => acceptCall(true));
  $('cancel-call-btn').addEventListener('click', cancelOutgoingCall);
  $('accept-btn').addEventListener('click', () => acceptCall(false));
  $('reject-btn').addEventListener('click', rejectCall);
  $('incoming-dialog').addEventListener('cancel', (e) => e.preventDefault()); // Échap ne doit pas ignorer l'appel
  $('leave-btn').addEventListener('click', leave);

  $('mic-btn').addEventListener('click', toggleMic);
  $('cam-btn').addEventListener('click', toggleCam);
  $('audio-only-btn').addEventListener('click', () => setAudioOnly(!state.audioOnly));
  $('hint-audio-only-btn').addEventListener('click', () => setAudioOnly(true));
  $('hint-close-btn').addEventListener('click', () => { $('degraded-hint').hidden = true; });
  $('screen-btn').addEventListener('click', toggleScreenShare);
  $('hangup-btn').addEventListener('click', hangUp);
  makeDraggable($('local-pip'), $('screen-call'));

  for (const id of ['waiting-chat-btn', 'call-chat-btn']) $(id).addEventListener('click', toggleDrawer);
  $('chat-close-btn').addEventListener('click', closeDrawer);
  $('drawer-backdrop').addEventListener('click', closeDrawer);
  $('chat-form').addEventListener('submit', (e) => { e.preventDefault(); sendChatMessage(); });
  $('chat-input').addEventListener('input', (e) => autosize(e.target));
  $('chat-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendChatMessage(); }
  });
  $('file-input').addEventListener('change', (e) => {
    const file = e.target.files && e.target.files[0];
    e.target.value = '';
    if (file) uploadFile(file);
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && state.chatOpen) closeDrawer(); });

  // Fermeture de l'onglet : raccrocher proprement (audit « HANGUP » plutôt que coupure).
  window.addEventListener('pagehide', () => {
    if (state.call && state.call.phase === 'active' && signaling) signaling.send('HANGUP', { reason: 'page-closed' });
    if (signaling) signaling.close(1000, 'page closed');
  });
}

/**
 * Caméra ou micro perdu (débranché, coupé par le système, pris par une autre
 * application) : on récupère un périphérique disponible et on le réinjecte dans
 * l'appel par replaceTrack(), sans raccrocher ni renégocier.
 */
const recovering = { audio: false, video: false };
async function recoverDevice(kind) {
  if (recovering[kind]) return;
  recovering[kind] = true;
  const what = kind === 'audio' ? 'Le micro' : 'La caméra';
  try {
    const track = await media.reacquire(kind);
    if (kind === 'audio') {
      track.enabled = state.micOn;
      meter.attach(track);
    }
    if (state.call && state.call.rtc) await state.call.rtc.replaceLocalTrack(kind, track);
    else if (kind === 'video') track.enabled = state.camOn && !state.audioOnly;
    attachLocalPreviews();
    applyLocalTrackStates();
    if (state.call) renderCallControls();
    toast(`${what} a été rétabli(e) automatiquement.`, 'success', 3000);
  } catch (err) {
    console.warn('[media] récupération impossible', err);
    toast(`${what} n'est plus disponible. ${describeMediaError(err)}`, 'error', 7000);
  } finally {
    recovering[kind] = false;
  }
}

function leave() {
  if (signaling) signaling.close(1000, 'leave');
  meter.detach();
  media.stop();
  const target = state.ctx.role === 'MEDECIN' ? 'doctor/patient.jsp' : 'view_appointment.jsp';
  location.href = new URL(target, appRoot).href;
}

bindUi();
boot().catch((err) => {
  console.error(err);
  showFatal('Erreur inattendue', err.message || String(err));
});
