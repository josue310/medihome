package com.teleconsult.rest;

import java.net.URI;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.teleconsult.dto.AttachmentDto;
import com.teleconsult.dto.ChatMessageDto;
import com.teleconsult.dto.ChatMessageRequest;
import com.teleconsult.dto.ConsultationContextDto;
import com.teleconsult.model.Attachment;
import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.model.ChatMessage;
import com.teleconsult.service.AttachmentService;
import com.teleconsult.service.ChatService;
import com.teleconsult.service.TeleconsultationConfig;
import com.teleconsult.service.TeleconsultationException;
import com.teleconsult.service.TurnCredentialService;
import com.util.CsrfUtil;

/**
 * API REST d'une consultation. Authentification et autorisation : voir
 * ConsultationAuthFilter (exécuté avant chaque méthode).
 *
 * Les POST passent aussi par le filtre CSRF de l'application : le client doit
 * envoyer l'en-tête X-CSRF-Token (valeur de ${sessionScope.csrfToken}).
 */
@Path("/consultations/{consultationId}")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ConsultationResource {

	@Inject
	private TurnCredentialService turnCredentials;

	@Inject
	private ChatService chatService;

	@Inject
	private AttachmentService attachmentService;

	@Inject
	private TeleconsultationConfig config;

	/**
	 * Contexte de la page cliente : identité (senderId à utiliser), rôle, jeton CSRF
	 * de la session et limites. Jamais mis en cache.
	 */
	@GET
	@Path("context")
	public Response context(@Context HttpServletRequest request) {
		AuthenticatedParticipant p = participant(request);
		ConsultationContextDto dto = new ConsultationContextDto(p.getConsultationId(), p.getUserId(),
				p.getRole().name(), p.getDisplayName(), CsrfUtil.getToken(request.getSession()),
				config.getMaxUploadBytes(), config.getMaxChatMessageLength());
		return Response.ok(dto).cacheControl(noStore()).build();
	}

	/** Serveurs STUN/TURN avec identifiants TURN temporaires (2 h). Jamais mis en cache. */
	@GET
	@Path("ice-servers")
	public Response iceServers(@Context HttpServletRequest request) {
		return Response.ok(turnCredentials.generate(participant(request))).cacheControl(noStore()).build();
	}

	/**
	 * Téléchargement d'un document de la consultation. ?disposition=inline pour
	 * l'afficher dans le navigateur (PDF/image). Durcissement : type MIME vérifié
	 * au dépôt, nosniff, CSP « sandbox » (aucun script même si le fichier est ouvert).
	 */
	@GET
	@Path("attachments/{attachmentId}")
	@Produces(MediaType.WILDCARD)
	public Response downloadAttachment(@Context HttpServletRequest request,
			@PathParam("attachmentId") long attachmentId, @QueryParam("disposition") String disposition) {
		Attachment a = attachmentService.find(participant(request), attachmentId);
		String mode = "inline".equals(disposition) ? "inline" : "attachment";
		return Response.ok(attachmentService.fileOf(a), a.getMimeType())
				.header("Content-Disposition", contentDisposition(mode, a.getOriginalName()))
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")
				.cacheControl(noStore()).build();
	}

	private static CacheControl noStore() {
		CacheControl cc = new CacheControl();
		cc.setNoStore(true);
		cc.setNoCache(true);
		cc.setPrivate(true);
		return cc;
	}

	/** Content-Disposition RFC 6266 : nom ASCII de repli + nom UTF-8 encodé (filename*). */
	static String contentDisposition(String mode, String fileName) {
		String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_").replace("\\", "_");
		String encoded;
		try {
			encoded = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
		} catch (java.io.UnsupportedEncodingException e) {
			encoded = ascii;
		}
		return mode + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
	}

	/** Enregistre un message de chat et notifie la salle (CHAT_NOTIFY). */
	@POST
	@Path("messages")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response postMessage(@Context HttpServletRequest request, ChatMessageRequest body) {
		if (body == null) {
			throw TeleconsultationException.badRequest("EMPTY_MESSAGE", "JSON body { \"content\": ... } is required");
		}
		ChatMessage saved = chatService.postMessage(participant(request), body.getContent());
		return Response.created(URI.create("messages/" + saved.getId())).entity(ChatMessageDto.from(saved)).build();
	}

	/** Dépose un document (champ multipart « file » ; PDF/JPEG/PNG ; 10 Mo max) et notifie la salle. */
	@POST
	@Path("attachments")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadAttachment(@Context HttpServletRequest request, @FormDataParam("file") FormDataBodyPart file) {
		if (file == null) {
			throw TeleconsultationException.badRequest("FILE_REQUIRED", "A multipart field named 'file' is required");
		}
		String declaredType = file.getMediaType() == null ? null : file.getMediaType().toString();
		String fileName = file.getContentDisposition() == null ? null : file.getContentDisposition().getFileName();
		Attachment saved = attachmentService.store(participant(request), file.getValueAs(java.io.InputStream.class),
				declaredType, fileName);
		return Response.created(URI.create("attachments/" + saved.getId())).entity(AttachmentDto.from(saved))
				.build();
	}

	private static AuthenticatedParticipant participant(HttpServletRequest request) {
		Object p = request.getAttribute(ConsultationAuthFilter.PARTICIPANT_PROPERTY);
		if (!(p instanceof AuthenticatedParticipant)) {
			// ne devrait jamais arriver : le filtre a refusé la requête avant
			throw TeleconsultationException.unauthorized("Not authenticated");
		}
		return (AuthenticatedParticipant) p;
	}
}
