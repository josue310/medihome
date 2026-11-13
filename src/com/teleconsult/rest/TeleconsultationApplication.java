package com.teleconsult.rest;

import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

/** Application JAX-RS (Jersey 2.25, compatible Java 7 / Servlet 3.0) exposée sous /api. */
@ApplicationPath("/api")
public class TeleconsultationApplication extends ResourceConfig {

	public TeleconsultationApplication() {
		packages("com.teleconsult.rest");
		register(JacksonFeature.class); // JSON (Jackson)
		register(MultiPartFeature.class); // multipart/form-data (pièces jointes)
	}
}
