package com.teleconsult.rest;

import java.io.IOException;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.service.ConsultationSecurityService;
import com.teleconsult.service.TeleconsultationConfig;
import com.teleconsult.service.TeleconsultationException;

/**
 * Sécurise toutes les ressources /api/consultations/{consultationId}/... :
 * l'utilisateur de la session HTTP doit être le médecin ou le patient de la
 * consultation (sinon 401/403, au format JSON).
 *
 * Ce filtre s'exécute APRÈS la sélection de la ressource mais AVANT la lecture
 * du corps : un utilisateur non autorisé ne peut donc pas faire écrire 10 Mo
 * sur le disque du serveur. Pour les envois multipart, la taille annoncée
 * (Content-Length) est contrôlée ici aussi.
 *
 * L'identité vérifiée est transmise à la ressource via une propriété de requête.
 */
@Provider
@ApplicationScoped // bean CDI : nécessaire pour que Jersey (jersey-cdi1x) y injecte les services CDI
@Priority(Priorities.AUTHENTICATION)
public class ConsultationAuthFilter implements ContainerRequestFilter {

	static final String PARTICIPANT_PROPERTY = "teleconsult.participant";
	/** Marge pour les en-têtes multipart autour du fichier. */
	private static final long MULTIPART_OVERHEAD = 64 * 1024;

	@Context
	private HttpServletRequest request;

	@Inject
	private ConsultationSecurityService security;

	@Inject
	private TeleconsultationConfig config;

	@Override
	public void filter(ContainerRequestContext ctx) throws IOException {
		String consultationId = ctx.getUriInfo().getPathParameters().getFirst("consultationId");
		if (consultationId == null) {
			return; // ressource hors consultation
		}
		String role = ctx.getUriInfo().getQueryParameters().getFirst("role");
		AuthenticatedParticipant participant = security.authenticate(request.getSession(false), consultationId,
				role);
		ctx.setProperty(PARTICIPANT_PROPERTY, participant);

		MediaType type = ctx.getMediaType();
		if (type != null && MediaType.MULTIPART_FORM_DATA_TYPE.isCompatible(type)) {
			int length = ctx.getLength();
			if (length < 0) {
				throw new TeleconsultationException(411, "LENGTH_REQUIRED", "Content-Length header is required");
			}
			if (length > config.getMaxUploadBytes() + MULTIPART_OVERHEAD) {
				throw TeleconsultationException.payloadTooLarge("Upload exceeds the maximum allowed size");
			}
		}
	}
}
