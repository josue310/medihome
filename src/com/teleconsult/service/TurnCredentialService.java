package com.teleconsult.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bouncycastle.util.encoders.Base64;

import com.teleconsult.dto.IceServerDto;
import com.teleconsult.dto.IceServersResponse;
import com.teleconsult.model.AuthenticatedParticipant;

/**
 * Identifiants TURN temporaires, compatibles Coturn en mode « TURN REST API »
 * (use-auth-secret + static-auth-secret=<coturn.secret>) :
 *
 *   username   = "<expirationTimestamp>:<userId>"     (timestamp en secondes epoch)
 *   credential = Base64( HMAC-SHA1( key = coturnSecret, message = username ) )
 *
 * Coturn recalcule le même HMAC et refuse l'identifiant après expiration :
 * aucun mot de passe TURN permanent n'est jamais transmis au navigateur.
 */
@ApplicationScoped
public class TurnCredentialService {

	@Inject
	private TeleconsultationConfig config;

	public IceServersResponse generate(AuthenticatedParticipant participant) {
		long ttl = config.getTurnCredentialTtlSeconds();
		long expiresAt = System.currentTimeMillis() / 1000L + ttl;

		List<IceServerDto> servers = new ArrayList<IceServerDto>();
		if (!config.getStunUrls().isEmpty()) {
			servers.add(new IceServerDto(config.getStunUrls(), null, null));
		}
		String secret = config.getCoturnSecret();
		if (!config.getTurnUrls().isEmpty() && !secret.isEmpty()) {
			// Coturn auto-hébergé (use-auth-secret) : identifiants temporaires signés.
			String username = expiresAt + ":" + participant.getUserId();
			servers.add(new IceServerDto(config.getTurnUrls(), username, hmacSha1Base64(secret, username)));
		} else if (!config.getTurnUrls().isEmpty() && !config.getTurnUsername().isEmpty()) {
			// Service TURN tiers (ex. offre gratuite Open Relay / Metered) : identifiants fixes
			// fournis par le service, transmis uniquement aux participants authentifiés.
			servers.add(new IceServerDto(config.getTurnUrls(), config.getTurnUsername(), config.getTurnCredential()));
		}
		return new IceServersResponse(servers, ttl, expiresAt);
	}

	/** Base64(HMAC-SHA1(secret, data)) : visible pour les tests. */
	static String hmacSha1Base64(String secret, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			return Base64.toBase64String(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw TeleconsultationException.internal("HMAC-SHA1 unavailable", e);
		}
	}
}
