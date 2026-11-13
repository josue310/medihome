package com.teleconsult.dto;

import java.util.List;

/**
 * Réponse de GET /api/consultations/{id}/ice-servers. Le champ iceServers peut
 * être passé tel quel à new RTCPeerConnection({ iceServers: ... }).
 */
public class IceServersResponse {

	private List<IceServerDto> iceServers;
	private long ttlSeconds;
	/** Expiration des identifiants TURN, en secondes epoch. */
	private long expiresAt;

	public IceServersResponse() {
	}

	public IceServersResponse(List<IceServerDto> iceServers, long ttlSeconds, long expiresAt) {
		this.iceServers = iceServers;
		this.ttlSeconds = ttlSeconds;
		this.expiresAt = expiresAt;
	}

	public List<IceServerDto> getIceServers() {
		return iceServers;
	}

	public void setIceServers(List<IceServerDto> iceServers) {
		this.iceServers = iceServers;
	}

	public long getTtlSeconds() {
		return ttlSeconds;
	}

	public void setTtlSeconds(long ttlSeconds) {
		this.ttlSeconds = ttlSeconds;
	}

	public long getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(long expiresAt) {
		this.expiresAt = expiresAt;
	}
}
