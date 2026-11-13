package com.teleconsult.websocket;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.service.ConsultationSecurityService;
import com.teleconsult.service.TeleconsultationException;

/**
 * Authentification lors de la poignée de main WebSocket (requête HTTP Upgrade).
 *
 * 1. Anti « Cross-Site WebSocket Hijacking » : si l'en-tête Origin est présent
 *    (toujours le cas depuis un navigateur), il doit correspondre à l'hôte du
 *    serveur ; sinon un site tiers pourrait ouvrir la socket avec les cookies
 *    de la victime.
 * 2. Identité tirée de la session HTTP (JSESSIONID) déjà authentifiée par les
 *    servlets de connexion ; vérification via ConsultationSecurityService que
 *    l'utilisateur est bien le médecin ou le patient de la consultation.
 *
 * Résultat stocké dans les propriétés de la configuration : Tomcat (>= 7.0.57)
 * fournit une copie PAR CONNEXION (WsPerSessionServerEndpointConfig), donc pas de
 * mélange entre utilisateurs. En cas de refus, l'endpoint ferme immédiatement la
 * connexion (code 1008 VIOLATED_POLICY) avant tout traitement de message.
 */
public class ConsultationHandshakeConfigurator extends ServerEndpointConfig.Configurator {

	private static final Logger LOG = Logger.getLogger(ConsultationHandshakeConfigurator.class.getName());

	static final String KEY_PARTICIPANT = "teleconsult.participant";
	static final String KEY_HTTP_SESSION = "teleconsult.httpSession";
	static final String KEY_REJECTION = "teleconsult.rejection";

	@Override
	public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
		Map<String, Object> props = sec.getUserProperties();
		props.remove(KEY_PARTICIPANT);
		props.remove(KEY_HTTP_SESSION);
		props.remove(KEY_REJECTION);
		try {
			checkOrigin(request);
			HttpSession httpSession = (HttpSession) request.getHttpSession();
			String consultationId = lastPathSegment(request.getRequestURI());
			String role = firstValue(request.getParameterMap(), "role");

			AuthenticatedParticipant participant = CDI.current().select(ConsultationSecurityService.class).get()
					.authenticate(httpSession, consultationId, role);
			props.put(KEY_PARTICIPANT, participant);
			props.put(KEY_HTTP_SESSION, httpSession);
		} catch (TeleconsultationException e) {
			LOG.info("Poignée de main WebSocket refusée : " + e.getCode());
			props.put(KEY_REJECTION, e.getCode());
		} catch (RuntimeException e) {
			LOG.log(Level.SEVERE, "Erreur pendant la poignée de main WebSocket", e);
			props.put(KEY_REJECTION, "INTERNAL_ERROR");
		}
	}

	/** Endpoint construit avec ses dépendances CDI (pas de fuite d'instances @Dependent). */
	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
		if (endpointClass == ConsultationEndpoint.class) {
			return endpointClass.cast(new ConsultationEndpoint(
					CDI.current().select(ConsultationRoomManager.class).get(),
					CDI.current().select(SignalingRouter.class).get()));
		}
		return super.getEndpointInstance(endpointClass);
	}

	private static void checkOrigin(HandshakeRequest request) {
		String origin = header(request, "Origin");
		if (origin == null) {
			return; // client non navigateur : pas de risque CSWSH (il n'a pas les cookies d'un tiers)
		}
		String host = header(request, "Host");
		try {
			URI o = new URI(origin);
			String originHost = o.getHost() + (o.getPort() > 0 ? ":" + o.getPort() : "");
			if (host != null && originHost.equalsIgnoreCase(host)) {
				return;
			}
		} catch (Exception ignored) {
			// Origin mal formé : refusé ci-dessous
		}
		throw TeleconsultationException.forbidden("ORIGIN_NOT_ALLOWED", "Cross-origin WebSocket refused");
	}

	/** Recherche d'en-tête insensible à la casse. */
	private static String header(HandshakeRequest request, String name) {
		for (Map.Entry<String, List<String>> e : request.getHeaders().entrySet()) {
			if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null
					&& !e.getValue().isEmpty()) {
				return e.getValue().get(0);
			}
		}
		return null;
	}

	private static String firstValue(Map<String, List<String>> params, String name) {
		List<String> values = params == null ? null : params.get(name);
		return values == null || values.isEmpty() ? null : values.get(0);
	}

	private static String lastPathSegment(URI uri) {
		String path = uri == null ? null : uri.getPath();
		if (path == null) {
			return null;
		}
		return path.substring(path.lastIndexOf('/') + 1);
	}
}
