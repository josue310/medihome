/**
 * media-devices.js — Accès caméra/micro, sélection des périphériques, vu-mètre
 * (Web Audio API) et Wake Lock. Aucune dépendance.
 */

/** Contraintes audio adaptées à la voix (annulation d'écho, réduction de bruit). */
const AUDIO_DEFAULTS = { echoCancellation: true, noiseSuppression: true, autoGainControl: true };
/** Vidéo 720p max : bon compromis qualité / débit pour une consultation. */
const VIDEO_DEFAULTS = { width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 24, max: 30 } };

/**
 * Traduit une erreur getUserMedia / getDisplayMedia en message clair pour le
 * patient ou le médecin.
 */
export function describeMediaError(error) {
  const name = error && error.name;
  switch (name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
      return "L'accès à la caméra ou au micro a été refusé. Cliquez sur l'icône de cadenas (ou de caméra) " +
        "dans la barre d'adresse, autorisez la caméra et le micro pour ce site, puis rechargez la page.";
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return 'Aucune caméra ni aucun micro détecté. Branchez un périphérique puis cliquez sur « Réessayer ».';
    case 'NotReadableError':
    case 'TrackStartError':
      return 'La caméra ou le micro est déjà utilisé par une autre application (Teams, Zoom, Skype…). ' +
        'Fermez-la puis cliquez sur « Réessayer ».';
    case 'OverconstrainedError':
      return "Le périphérique sélectionné n'est plus disponible. Choisissez-en un autre dans la liste.";
    case 'SecurityError':
      return 'Le navigateur bloque la caméra sur cette page : la télé-consultation doit être ouverte en HTTPS.';
    case 'AbortError':
      return "L'accès au périphérique a été interrompu. Réessayez.";
    default:
      return `Impossible d'accéder à la caméra ou au micro (${name || 'erreur inconnue'}).`;
  }
}

/** Vrai si le navigateur permet l'accès aux médias (contexte sécurisé : HTTPS ou localhost). */
export function isMediaSupported() {
  return !!(window.isSecureContext && navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
}

/**
 * Gère le flux local (caméra + micro) et la liste des périphériques.
 * Événements : 'devicechange' (la liste a changé), 'trackended' (périphérique débranché).
 */
export class MediaDevicesManager extends EventTarget {
  constructor() {
    super();
    /** @type {MediaStream|null} */
    this.stream = null;
    this.audioDeviceId = null;
    this.videoDeviceId = null;
    /** Avertissement non bloquant (ex. : « aucune caméra, appel audio uniquement »). */
    this.warning = null;
    this._onDeviceChange = () => this.dispatchEvent(new Event('devicechange'));
    if (navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
      navigator.mediaDevices.addEventListener('devicechange', this._onDeviceChange);
    }
  }

  get audioTrack() { return this.stream ? this.stream.getAudioTracks()[0] || null : null; }

  get videoTrack() { return this.stream ? this.stream.getVideoTracks()[0] || null : null; }

  /**
   * Ouvre (ou rouvre) la caméra et le micro choisis. Si aucune caméra n'est
   * disponible, bascule automatiquement en micro seul (avertissement, pas d'erreur).
   * @throws l'erreur d'origine (DOMException) si même le micro est inaccessible.
   */
  async open({ audioDeviceId = this.audioDeviceId, videoDeviceId = this.videoDeviceId } = {}) {
    if (!isMediaSupported()) {
      const err = new Error('insecure context');
      err.name = 'SecurityError';
      throw err;
    }
    this.stop();
    this.warning = null;
    const audio = { ...AUDIO_DEFAULTS, ...(audioDeviceId ? { deviceId: { exact: audioDeviceId } } : {}) };
    const video = { ...VIDEO_DEFAULTS, ...(videoDeviceId ? { deviceId: { exact: videoDeviceId } } : {}) };
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ audio, video });
    } catch (err) {
      if (err.name === 'NotAllowedError' || err.name === 'SecurityError') throw err;
      // Caméra absente ou occupée : on tente le micro seul.
      try {
        this.stream = await navigator.mediaDevices.getUserMedia({ audio, video: false });
        this.warning = 'Caméra indisponible : vous serez en audio uniquement. ' + describeMediaError(err);
      } catch (audioErr) {
        throw err.name === 'NotFoundError' ? audioErr : err;
      }
    }
    const a = this.audioTrack;
    const v = this.videoTrack;
    this.audioDeviceId = a ? a.getSettings().deviceId || audioDeviceId : null;
    this.videoDeviceId = v ? v.getSettings().deviceId || videoDeviceId : null;
    for (const track of this.stream.getTracks()) {
      track.addEventListener('ended', () =>
        this.dispatchEvent(new CustomEvent('trackended', { detail: { kind: track.kind } })));
    }
    return this.stream;
  }

  /**
   * Récupère une nouvelle piste quand la précédente s'est terminée en cours de route
   * (périphérique débranché, coupé par le système ou pris par une autre application).
   * La piste remplace l'ancienne dans le même MediaStream ; l'appelant la
   * réinjecte dans l'appel via RTCRtpSender.replaceTrack().
   * @param {'audio'|'video'} kind
   * @returns {Promise<MediaStreamTrack>}
   */
  async reacquire(kind) {
    const constraints = kind === 'audio'
      ? { audio: { ...AUDIO_DEFAULTS }, video: false }
      : { audio: false, video: { ...VIDEO_DEFAULTS } }; // n'importe quel périphérique disponible
    const fresh = await navigator.mediaDevices.getUserMedia(constraints);
    const track = fresh.getTracks()[0];
    const old = kind === 'audio' ? this.audioTrack : this.videoTrack;
    if (!this.stream) this.stream = new MediaStream();
    if (old) {
      this.stream.removeTrack(old);
      old.stop();
    }
    this.stream.addTrack(track);
    if (kind === 'audio') this.audioDeviceId = track.getSettings().deviceId || null;
    else this.videoDeviceId = track.getSettings().deviceId || null;
    track.addEventListener('ended', () =>
      this.dispatchEvent(new CustomEvent('trackended', { detail: { kind: track.kind } })));
    return track;
  }

  /**
   * Libère la caméra (appel audio) : la piste est arrêtée, le voyant de la
   * caméra s'éteint. Elle pourra être récupérée plus tard avec reacquire('video').
   */
  releaseVideo() {
    const track = this.videoTrack;
    if (!track) return;
    this.stream.removeTrack(track);
    track.stop(); // stop() volontaire : ne déclenche pas l'événement 'ended'
  }

  /** Liste les périphériques (les libellés ne sont visibles qu'après autorisation). */
  async listDevices() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
      return { audioInputs: [], videoInputs: [] };
    }
    const devices = await navigator.mediaDevices.enumerateDevices();
    const label = (d, i, kind) => d.label || `${kind} ${i + 1}`;
    return {
      audioInputs: devices.filter(d => d.kind === 'audioinput')
        .map((d, i) => ({ id: d.deviceId, label: label(d, i, 'Microphone') })),
      videoInputs: devices.filter(d => d.kind === 'videoinput')
        .map((d, i) => ({ id: d.deviceId, label: label(d, i, 'Caméra') })),
    };
  }

  /** Arrête toutes les pistes locales (libère la caméra : le voyant s'éteint). */
  stop() {
    if (this.stream) {
      this.stream.getTracks().forEach(t => t.stop());
      this.stream = null;
    }
  }

  dispose() {
    this.stop();
    if (navigator.mediaDevices && navigator.mediaDevices.removeEventListener) {
      navigator.mediaDevices.removeEventListener('devicechange', this._onDeviceChange);
    }
  }
}

/**
 * Vu-mètre : niveau sonore du micro en temps réel (AudioContext + AnalyserNode).
 * onLevel(niveau) est appelé à chaque image (≈ 60 fois/s) avec une valeur 0..1.
 */
export class AudioLevelMeter {
  /** @param {AudioContext} audioContext contexte partagé (créé lors d'un geste utilisateur) */
  constructor(audioContext, onLevel) {
    this.ctx = audioContext;
    this.onLevel = onLevel;
    this.source = null;
    this.analyser = null;
    this.frame = 0;
    this.smoothed = 0;
  }

  /** Branche le vu-mètre sur une piste audio (remplace la précédente). */
  attach(audioTrack) {
    this.detach();
    if (!audioTrack || !this.ctx) {
      this.onLevel(0);
      return;
    }
    this.source = this.ctx.createMediaStreamSource(new MediaStream([audioTrack]));
    this.analyser = this.ctx.createAnalyser();
    this.analyser.fftSize = 1024;
    this.analyser.smoothingTimeConstant = 0.3;
    this.source.connect(this.analyser); // pas de connexion à destination : aucun retour sonore
    const samples = new Float32Array(this.analyser.fftSize);
    const loop = () => {
      this.analyser.getFloatTimeDomainData(samples);
      let sum = 0;
      for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i];
      const rms = Math.sqrt(sum / samples.length);
      // Échelle perceptive (≈ dB) puis lissage : montée rapide, descente douce.
      const level = Math.min(1, Math.max(0, (20 * Math.log10(rms + 1e-8) + 60) / 50));
      this.smoothed = level > this.smoothed ? level : this.smoothed * 0.85 + level * 0.15;
      this.onLevel(audioTrack.enabled ? this.smoothed : 0);
      this.frame = requestAnimationFrame(loop);
    };
    this.frame = requestAnimationFrame(loop);
  }

  detach() {
    cancelAnimationFrame(this.frame);
    if (this.source) this.source.disconnect();
    this.source = null;
    this.analyser = null;
    this.smoothed = 0;
  }
}

/**
 * Wake Lock : empêche la mise en veille de l'écran (smartphone) pendant l'appel.
 * Le navigateur libère automatiquement le verrou quand l'onglet passe en arrière-plan :
 * on le redemande au retour au premier plan tant que l'appel est actif.
 */
export class ScreenWakeLock {
  constructor() {
    this.sentinel = null;
    this.wanted = false;
    this._onVisibility = () => {
      if (this.wanted && document.visibilityState === 'visible' && !this.sentinel) this._acquire();
    };
    document.addEventListener('visibilitychange', this._onVisibility);
  }

  get supported() { return 'wakeLock' in navigator; }

  async enable() {
    this.wanted = true;
    await this._acquire();
  }

  async disable() {
    this.wanted = false;
    if (this.sentinel) {
      try { await this.sentinel.release(); } catch (e) { /* déjà libéré */ }
      this.sentinel = null;
    }
  }

  async _acquire() {
    if (!this.supported || this.sentinel) return;
    try {
      this.sentinel = await navigator.wakeLock.request('screen');
      this.sentinel.addEventListener('release', () => { this.sentinel = null; });
    } catch (e) {
      // Refus (batterie faible, onglet masqué…) : non bloquant.
      console.info('[wake-lock] indisponible :', e.name);
    }
  }
}
