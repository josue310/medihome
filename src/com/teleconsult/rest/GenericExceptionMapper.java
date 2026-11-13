package com.teleconsult.rest;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.teleconsult.dto.ErrorResponse;

/**
 * Filet de sécurité : toute autre exception devient une réponse JSON propre,
 * sans trace de pile renvoyée au client (la trace est journalisée côté serveur).
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

	private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

	@Override
	public Response toResponse(Throwable t) {
		if (t instanceof WebApplicationException) {
			int status = ((WebApplicationException) t).getResponse().getStatus();
			Response.StatusType st = Response.Status.fromStatusCode(status);
			return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
					.entity(new ErrorResponse("HTTP_" + status, st != null ? st.getReasonPhrase() : "Error")).build();
		}
		LOG.log(Level.SEVERE, "Erreur non gérée dans l'API téléconsultation", t);
		return Response.status(500).type(MediaType.APPLICATION_JSON_TYPE)
				.entity(new ErrorResponse("INTERNAL_ERROR", "Internal server error")).build();
	}
}
