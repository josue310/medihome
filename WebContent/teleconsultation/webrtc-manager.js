/**
 * webrtc-manager.js — Cycle de vie d'une RTCPeerConnection 1-à-1.
 *
 * Rôles de négociation : l'APPELANT est toujours l'« offerer » (il crée les offres,
 * y compris celles d'ICE restart) ; l'appelé répond. Cela évite les collisions
 * d'offres (glare). Si l'appelé détecte une coupure, il envoie ICE_RESTART pour
 * demander à l'appelant de relancer la négociation.
 *
 * Une piste audio et une piste vidéo sont TOUJOURS négociées (transceivers) même
 * sans caméra : le partage d'écran et le retour de la caméra se font alors par
 * RTCRtpSender.replaceTrack(), sans renégociation.
 *
 * Événements : 'remotetrack' {track, stream}, 'connectionstate' {state},
 *   'quality' {level, lossPct, jitterMs, rttMs}, 'degraded', 'recovered',
 *   'icerestart' {reason}, 'screenshare' {active, track}, 'remotevideo' {active},
 *   'error' {message}
 */

const QUALITY = { GOOD: 'good', MEDIUM: 'medium', POOR: 'poor' };
const RESTART_MIN_INTERVAL_MS = 5000;
const DISCONNECTED_GRACE_MS = 2000;

export class WebRtcManager extends EventTarget {
  /**
   * @param {object} o
   * @param {import('./signaling-client.js').SignalingClient} o.signaling
   * @param {RTCIceServer[]} o.iceServers  serveurs fournis par GET /ice-servers
   * @param {MediaStream|null} o.localStream
   * @param {boolean} o.isOfferer          true pour l'appelant
   */
  constructor({ signaling, iceServers, localStream, isOfferer, statsIntervalMs = 3000 }) {
    super();
    this.signaling = signaling;
    this.isOfferer = isOfferer;
    this.localStream = localStream;
    this.cameraTrack = localStream ? localStream.getVideoTracks()[0] || null : null;
    this.micTrack = localStream ? localStream.getAudioTracks()[0] || null : null;
    this.remoteStream = new MediaStream();
    this.statsIntervalMs = statsIntervalMs;

    this.audioTransceiver = null;
    this.videoTransceiver = null;
    this.screenTrack = null;
    this.audioOnly = false;
    this.cameraEnabled = true;
    this.pendingCandidates = [];
    this.closed = false;
    this.lastRestartAt = 0;
    this.restartTimer = 0;
    this.disconnectTimer = 0;
    this.pendingRestart = false;
    this.statsTimer = 0;
    this.prevLost = 0;
    this.prevReceived = 0;
    this.prevVideoReceived = 0;
    this.remoteVideoActive = true;
    this.poorStreak = 0;
    this.degraded = false;

    this.pc = new RTCPeerConnection({ iceServers, bundlePolicy: 'max-bundle', rtcpMuxPolicy: 'require' });
    this._wirePeerConnection();
  }

  /** Appelant : crée les transceivers et envoie la première offre. Appelé : attend l'offre. */
  async start() {
    if (this.isOfferer) {
      this.audioTransceiver = this.pc.addTransceiver(this.micTrack || 'audio', this._transceiverInit(this.micTrack));
      this.videoTransceiver = this.pc.addTransceiver(this.cameraTrack || 'video', this._transceiverInit(this.cameraTrack));
      await this._negotiate(false);
    }
    this._startStats();
  }

  /** Traite un message de signalisation WebRTC venant du correspondant. */
  async handleSignal(msg) {
    if (this.closed) return;
    const p = msg.payload || {};
    try {
      switch (msg.type) {
        case 'SDP_OFFER':
          if (this.isOfferer) {
            console.warn('[webrtc] offre inattendue côté appelant ignorée');
            return;
          }
          await this._acceptOffer(p.sdp);
          break;
        case 'SDP_ANSWER':
          if (this.pc.signalingState !== 'have-local-offer') return; // réponse périmée
          await this.pc.setRemoteDescription({ type: 'answer', sdp: p.sdp });
          await this._flushCandidates();
          break;
        case 'ICE_CANDIDATE':
          await this._addRemoteCandidate(p);
          break;
        case 'ICE_RESTART':
          if (this.isOfferer) this.restartIce('demande du correspondant');
          break;
        default:
          break;
      }
    } catch (err) {
      console.error('[webrtc]', msg.type, err);
      this._emit('error', { message: `Négociation ${msg.type} : ${err.message}` });
    }
  }

  // ---------------------------------------------------------------- contrôles média

  /** Couper / rétablir le micro : track.enabled, sans renégociation. */
  setMicEnabled(on) {
    if (this.micTrack) this.micTrack.enabled = on;
  }

  /** Couper / rétablir la caméra : track.enabled (le correspondant reçoit une image noire). */
  setCameraEnabled(on) {
    this.cameraEnabled = on;
    if (this.cameraTrack && !this.audioOnly) this.cameraTrack.enabled = on;
  }

  /**
   * Mode « audio seul » (faible débit) : plus aucune vidéo n'est ENVOYÉE
   * (replaceTrack(null) libère réellement la bande passante, contrairement à
   * enabled=false qui envoie encore des images noires). Aucune renégociation.
   */
  async setAudioOnly(on) {
    this.audioOnly = on;
    if (on && this.screenTrack) await this.stopScreenShare();
    if (this.cameraTrack) this.cameraTrack.enabled = on ? false : this.cameraEnabled;
    if (this.videoTransceiver) await this.videoTransceiver.sender.replaceTrack(on ? null : this.cameraTrack);
  }

  /**
   * Remplace une piste locale (périphérique changé ou récupéré après débranchement)
   * sans renégociation. L'état micro/caméra/audio seul/partage d'écran est conservé.
   */
  async replaceLocalTrack(kind, track) {
    if (kind === 'audio') {
      this.micTrack = track;
      if (this.audioTransceiver) await this.audioTransceiver.sender.replaceTrack(track);
    } else {
      this.cameraTrack = track;
      track.enabled = this.cameraEnabled && !this.audioOnly;
      if (this.videoTransceiver && !this.screenTrack && !this.audioOnly) {
        await this.videoTransceiver.sender.replaceTrack(track);
      }
    }
  }

  /** Partage d'écran : remplace la piste vidéo envoyée ; la caméra revient à l'arrêt du partage. */
  async startScreenShare() {
    if (this.screenTrack || !this.videoTransceiver) return;
    const stream = await navigator.mediaDevices.getDisplayMedia({
      video: { frameRate: { ideal: 15, max: 30 } }, audio: false,
    });
    const track = stream.getVideoTracks()[0];
    track.contentHint = 'detail'; // privilégie la netteté (texte, radiographies)
    this.screenTrack = track;
    await this.videoTransceiver.sender.replaceTrack(track);
    // « Arrêter le partage » dans la barre du navigateur : retour automatique à la caméra.
    track.onended = () => this.stopScreenShare();
    this._emit('screenshare', { active: true, track });
  }

  async stopScreenShare() {
    const track = this.screenTrack;
    if (!track) return;
    this.screenTrack = null;
    track.onended = null;
    track.stop();
    if (this.videoTransceiver && !this.closed) {
      await this.videoTransceiver.sender.replaceTrack(this.audioOnly ? null : this.cameraTrack);
    }
    this._emit('screenshare', { active: false, track: null });
  }

  // ---------------------------------------------------------------- résilience

  /**
   * ICE restart : l'appelant appelle restartIce() puis envoie une nouvelle offre
   * (iceRestart: true) ; l'appelé demande à l'appelant de le faire (ICE_RESTART).
   * Limité à un essai toutes les 5 s ; différé si la signalisation est coupée.
   */
  restartIce(reason) {
    if (this.closed) return;
    const wait = this.lastRestartAt + RESTART_MIN_INTERVAL_MS - Date.now();
    if (wait > 0) {
      clearTimeout(this.restartTimer);
      this.restartTimer = setTimeout(() => this._restartIfStillBroken(reason), wait);
      return;
    }
    if (!this.signaling.connected) {
      this.pendingRestart = true; // sera relancé par onSignalingReconnected()
      return;
    }
    this.lastRestartAt = Date.now();
    this.pendingRestart = false;
    this._emit('icerestart', { reason });
    if (this.isOfferer) {
      if (typeof this.pc.restartIce === 'function') this.pc.restartIce();
      this._negotiate(true).catch(err => this._emit('error', { message: `ICE restart : ${err.message}` }));
    } else {
      this.signaling.send('ICE_RESTART', { reason });
    }
  }

  /** À appeler quand la WebSocket est rétablie. */
  onSignalingReconnected() {
    const state = this.pc.iceConnectionState;
    if (this.pendingRestart || state === 'disconnected' || state === 'failed') {
      this.lastRestartAt = 0;
      this.restartIce('signalisation rétablie');
    }
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    clearInterval(this.statsTimer);
    clearTimeout(this.restartTimer);
    clearTimeout(this.disconnectTimer);
    if (this.screenTrack) {
      this.screenTrack.onended = null;
      this.screenTrack.stop();
      this.screenTrack = null;
    }
    // Les pistes locales (caméra/micro) appartiennent à MediaDevicesManager : non arrêtées ici.
    this.pc.close();
  }

  // ---------------------------------------------------------------- interne

  _transceiverInit(track) {
    return { direction: 'sendrecv', streams: track && this.localStream ? [this.localStream] : [] };
  }

  _wirePeerConnection() {
    const pc = this.pc;
    pc.addEventListener('track', (event) => {
      if (!this.remoteStream.getTracks().includes(event.track)) this.remoteStream.addTrack(event.track);
      this._emit('remotetrack', { track: event.track, stream: this.remoteStream });
    });
    pc.addEventListener('icecandidate', (event) => {
      if (!event.candidate || !event.candidate.candidate) return;
      const c = event.candidate;
      this.signaling.send('ICE_CANDIDATE', {
        candidate: c.candidate, sdpMid: c.sdpMid, sdpMLineIndex: c.sdpMLineIndex,
        usernameFragment: c.usernameFragment,
      });
    });
    pc.addEventListener('iceconnectionstatechange', () => {
      const state = pc.iceConnectionState;
      this._emit('connectionstate', { state });
      clearTimeout(this.disconnectTimer);
      if (state === 'failed') {
        this.restartIce('connexion ICE échouée');
      } else if (state === 'disconnected') {
        // « disconnected » se rétablit souvent seul en 1-2 s (perte de paquets brève).
        this.disconnectTimer = setTimeout(() => {
          if (pc.iceConnectionState === 'disconnected') this.restartIce('connexion ICE perdue');
        }, DISCONNECTED_GRACE_MS);
      }
    });
  }

  _restartIfStillBroken(reason) {
    const s = this.pc.iceConnectionState;
    if (s === 'disconnected' || s === 'failed' || this.pendingRestart) this.restartIce(reason);
  }

  async _negotiate(iceRestart) {
    const offer = await this.pc.createOffer({ iceRestart });
    await this.pc.setLocalDescription(offer);
    this.signaling.send('SDP_OFFER', { type: 'offer', sdp: this.pc.localDescription.sdp, iceRestart });
  }

  async _acceptOffer(sdp) {
    await this.pc.setRemoteDescription({ type: 'offer', sdp });
    if (!this.audioTransceiver || !this.videoTransceiver) {
      // Première offre : on attache nos pistes aux transceivers créés par l'offre.
      for (const t of this.pc.getTransceivers()) {
        const kind = t.receiver.track.kind;
        if (kind === 'audio' && !this.audioTransceiver) this.audioTransceiver = t;
        else if (kind === 'video' && !this.videoTransceiver) this.videoTransceiver = t;
      }
      await this._attachLocal(this.audioTransceiver, this.micTrack);
      await this._attachLocal(this.videoTransceiver, this.audioOnly ? null : (this.screenTrack || this.cameraTrack));
    }
    await this._flushCandidates();
    const answer = await this.pc.createAnswer();
    await this.pc.setLocalDescription(answer);
    this.signaling.send('SDP_ANSWER', { type: 'answer', sdp: this.pc.localDescription.sdp });
  }

  async _attachLocal(transceiver, track) {
    if (!transceiver) return;
    transceiver.direction = 'sendrecv';
    await transceiver.sender.replaceTrack(track);
    if (track && this.localStream && typeof transceiver.sender.setStreams === 'function') {
      transceiver.sender.setStreams(this.localStream);
    }
  }

  async _addRemoteCandidate(p) {
    if (typeof p.candidate !== 'string' || p.candidate === '') return;
    const candidate = {
      candidate: p.candidate, sdpMid: p.sdpMid, sdpMLineIndex: p.sdpMLineIndex,
      usernameFragment: p.usernameFragment,
    };
    if (!this.pc.remoteDescription) {
      this.pendingCandidates.push(candidate); // arrivé avant l'offre/réponse
      return;
    }
    try {
      await this.pc.addIceCandidate(candidate);
    } catch (err) {
      // Candidat d'une génération ICE précédente (après restart) : sans conséquence.
      console.debug('[webrtc] candidat ignoré :', err.message);
    }
  }

  async _flushCandidates() {
    const queued = this.pendingCandidates.splice(0);
    for (const c of queued) {
      try { await this.pc.addIceCandidate(c); } catch (err) { console.debug('[webrtc] candidat ignoré'); }
    }
  }

  _startStats() {
    clearInterval(this.statsTimer);
    this.statsTimer = setInterval(() => {
      this._sampleStats().catch(err => console.debug('[webrtc] getStats', err));
    }, this.statsIntervalMs);
  }

  /** Qualité réseau toutes les 3 s : pertes de paquets (delta), gigue et RTT. */
  async _sampleStats() {
    if (this.closed || this.pc.connectionState === 'new') return;
    const report = await this.pc.getStats();
    let lost = 0;
    let received = 0;
    let videoReceived = 0;
    let audioJitter = null;
    let videoJitter = null;
    let rtt = null;
    report.forEach((s) => {
      if (s.type === 'inbound-rtp') {
        lost += Math.max(0, s.packetsLost || 0);
        received += s.packetsReceived || 0;
        if (s.kind === 'video') videoReceived += s.packetsReceived || 0;
        if (typeof s.jitter === 'number') {
          if (s.kind === 'audio') audioJitter = s.jitter;
          else videoJitter = s.jitter;
        }
      } else if (s.type === 'candidate-pair' && s.state === 'succeeded' && s.nominated
        && typeof s.currentRoundTripTime === 'number') {
        rtt = s.currentRoundTripTime;
      }
    });
    const dLost = lost - this.prevLost;
    const dReceived = received - this.prevReceived;
    const dVideo = videoReceived - this.prevVideoReceived;
    this.prevLost = lost;
    this.prevReceived = received;
    this.prevVideoReceived = videoReceived;

    // Vidéo distante en pause (correspondant en audio seul : replaceTrack(null)).
    // Chrome ne déclenche pas « mute » dans ce cas : on le déduit du flux de paquets.
    const remoteVideoActive = dVideo > 0;
    if (remoteVideoActive !== this.remoteVideoActive && dReceived > 0) {
      this.remoteVideoActive = remoteVideoActive;
      this._emit('remotevideo', { active: remoteVideoActive });
    }

    const lossPct = dLost + dReceived > 0 ? (100 * dLost) / (dLost + dReceived) : 0;
    const jitterMs = ((audioJitter != null ? audioJitter : videoJitter) || 0) * 1000;
    const rttMs = rtt != null ? rtt * 1000 : null;
    const connected = this.pc.iceConnectionState === 'connected' || this.pc.iceConnectionState === 'completed';

    let level;
    if (!connected || dReceived <= 0) level = QUALITY.POOR; // plus rien ne passe
    else if (lossPct < 2 && jitterMs < 30 && (rttMs == null || rttMs < 300)) level = QUALITY.GOOD;
    else if (lossPct < 8 && jitterMs < 80 && (rttMs == null || rttMs < 600)) level = QUALITY.MEDIUM;
    else level = QUALITY.POOR;

    this._emit('quality', { level, lossPct, jitterMs, rttMs });

    this.poorStreak = level === QUALITY.POOR ? this.poorStreak + 1 : 0;
    if (this.poorStreak >= 2 && !this.degraded) {
      this.degraded = true;
      this._emit('degraded', {});
    } else if (level === QUALITY.GOOD && this.degraded) {
      this.degraded = false;
      this._emit('recovered', {});
    }
  }

  _emit(name, detail) {
    this.dispatchEvent(new CustomEvent(name, { detail }));
  }
}
