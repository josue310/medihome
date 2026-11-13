package com.teleconsult.model;

/** Métadonnées d'un document déposé dans une consultation (le fichier est sur disque). */
public class Attachment {

	private long id;
	private int consultationId;
	private String senderId;
	private Role senderRole;
	/** Nom d'origine, nettoyé : uniquement affiché, jamais utilisé comme chemin. */
	private String originalName;
	/** Nom sur disque, aléatoire (UUID + extension déduite du type vérifié). */
	private String storedName;
	private String mimeType;
	private long sizeBytes;
	private String sha256;
	private long createdAt;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getConsultationId() {
		return consultationId;
	}

	public void setConsultationId(int consultationId) {
		this.consultationId = consultationId;
	}

	public String getSenderId() {
		return senderId;
	}

	public void setSenderId(String senderId) {
		this.senderId = senderId;
	}

	public Role getSenderRole() {
		return senderRole;
	}

	public void setSenderRole(Role senderRole) {
		this.senderRole = senderRole;
	}

	public String getOriginalName() {
		return originalName;
	}

	public void setOriginalName(String originalName) {
		this.originalName = originalName;
	}

	public String getStoredName() {
		return storedName;
	}

	public void setStoredName(String storedName) {
		this.storedName = storedName;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public void setSizeBytes(long sizeBytes) {
		this.sizeBytes = sizeBytes;
	}

	public String getSha256() {
		return sha256;
	}

	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}
}
