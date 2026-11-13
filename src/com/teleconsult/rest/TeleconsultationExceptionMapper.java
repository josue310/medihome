package com.teleconsult.rest;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.teleconsult.dto.ErrorResponse;
import com.teleconsult.service.TeleconsultationException;

/** Erreurs métier -> statut HTTP + { "error": CODE, "message": ... }. */
@Provider
public class TeleconsultationExceptionMapper implements ExceptionMapper<TeleconsultationException> {

	@Override
	public Response toResponse(TeleconsultationException e) {
		// Pas de détail interne pour les 500 (la cause est journalisée par le service).
		String message = e.getHttpStatus() >= 500 ? "Internal server error" : e.getMessage();
		return Response.status(e.getHttpStatus()).type(MediaType.APPLICATION_JSON_TYPE)
				.entity(new ErrorResponse(e.getCode(), message)).build();
	}
}
