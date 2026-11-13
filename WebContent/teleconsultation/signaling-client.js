/**
 * signaling-client.js — Client WebSocket de signalisation.
 *
 * - Enveloppe stricte : { type, consultationId, senderId, timestamp, payload } (aucun autre champ).
 * - Reconnexion automatique avec backoff exponentiel + gigue (0,5 s, 1 s, 2 s… plafonné à 8 s),
 *   immédiate au retour du réseau (événement « online »). Le serveur garde l'appel ouvert
 *   pendant un délai de grâce : se reconnecter vite permet de le reprendre.
 * - Pas de reconnexion après un refus définitif : 1008 (non autorisé / session terminée)
 *   ou 4001 (connexion remplacée par un autre onglet).
 *
 * Événements émis (EventTarget) :
 *   'open'         detail { reconnected }
 *   'close'        detail { code, reason, willReconnect, retryInMs }
 *   'fatal'        detail { code, reason }     (aucune reconnexion)
 *   'message'      detail <message>            (tous les messages)
 *   '<TYPE>'       detail <message>            (ex. 'CALL_REQUEST', 'CHAT_NOTIFY')
 */

const FATAL_CODES = new Set([1008, 4001]);

export class SignalingClient extends EventTarget {
  /**
   * @param {object} options
   * @param {string} options.url            URL ws(s)://…/ws/consultation/{id}
   * @param {string} options.consultationId identifiant de consultation (chaîne)
   * @param {string} options.senderId       identité fournie par le serveur (ex. « PATIENT-3 »)
   */
  constructor({ url, consultationId, senderId, baseDelayMs = 500, maxDelayMs = 8000 }) {
    super();
    this.url = url;
    this.consultationId = String(consultationId);
    this.senderId = senderId;
    this.baseDelayMs = baseDelayMs;
    this.maxDelayMs = maxDelayMs;
    this.ws = null;
    this.attempt = 0;
    this.retryTimer = 0;
    this.stopped = false;
    this.everOpened = false;
    this._onOnline = () => {
      if (!this.stopped && !this.connected && this.retryTimer) {
        clearTimeout(this.retryTimer);
        this.retryTimer = 0;
        this._open();
      }
    };
    window.addEventListener('online', this._onOnline);
  }

  get connected() { return !!this.ws && this.ws.readyState === WebSocket.OPEN; }

  connect() {
    this.stopped = false;
    this._open();
  }

  /**
   * Envoie un message. Retourne false (sans exception) si la socket n'est pas
   * ouverte : la signalisation périmée n'est pas mise en file d'attente.
   */
  send(type, payload = {}) {
    if (!this.connected) {
      console.warn('[signaling] non connecté, message ignoré :', type);
      return false;
    }
    this.ws.send(JSON.stringify({
      type,
      consultationId: this.consultationId,
      senderId: this.senderId,
      timestamp: Date.now(),
      payload: payload || {},
    }));
    return true;
  }

  /** Fermeture volontaire (sans reconnexion). */
  close(code = 1000, reason = 'leave') {
    this.stopped = true;
    clearTimeout(this.retryTimer);
    this.retryTimer = 0;
    window.removeEventListener('online', this._onOnline);
    if (this.ws) {
      try { this.ws.close(code, reason); } catch (e) { /* déjà fermée */ }
    }
  }

  _open() {
    const ws = new WebSocket(this.url);
    this.ws = ws;
    ws.addEventListener('open', () => {
      if (ws !== this.ws) return;
      const reconnected = this.everOpened;
      this.everOpened = true;
      this.attempt = 0;
      this._emit('open', { reconnected });
    });
    ws.addEventListener('message', (event) => {
      if (ws !== this.ws) return;
      let msg;
      try {
        msg = JSON.parse(event.data);
      } catch (e) {
        console.warn('[signaling] message non JSON ignoré');
        return;
      }
      if (!msg || typeof msg.type !== 'string') return;
      this._emit('message', msg);
      this._emit(msg.type, msg);
    });
    ws.addEventListener('close', (event) => {
      if (ws !== this.ws) return;
      this.ws = null;
      if (this.stopped) {
        this._emit('close', { code: event.code, reason: event.reason, willReconnect: false });
        return;
      }
      if (FATAL_CODES.has(event.code)) {
        this.stopped = true;
        this._emit('fatal', { code: event.code, reason: event.reason });
        return;
      }
      const retryInMs = this._nextDelay();
      this._emit('close', { code: event.code, reason: event.reason, willReconnect: true, retryInMs });
      this.retryTimer = setTimeout(() => {
        this.retryTimer = 0;
        if (!this.stopped) this._open();
      }, retryInMs);
    });
    // 'error' est toujours suivi de 'close' : la reconnexion est gérée là.
  }

  _nextDelay() {
    const exp = Math.min(this.maxDelayMs, this.baseDelayMs * 2 ** this.attempt);
    this.attempt++;
    return Math.round(exp * (0.7 + Math.random() * 0.3)); // gigue : évite les reconnexions synchronisées
  }

  _emit(name, detail) {
    this.dispatchEvent(new CustomEvent(name, { detail }));
  }
}
