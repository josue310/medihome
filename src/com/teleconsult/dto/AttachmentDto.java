package com.teleconsult.dto;

import com.teleconsult.model.Attachment;

/**
 * Représentation JSON d'un document déposé. Le nom de stockage sur disque
 * n'est volontairement pas exposé.
 */
public class AttachmentDto {

	private long id;
	private int consultationId;
	private String senderId;
	private String senderRole;
	private String fileName;
	private String mimeType;
	private long sizeBytes;
	private String sha256;
	private long createdAt;

	public static AttachmentDto from(Attachment a) {
		AttachmentDto d = new AttachmentDto();
		d.id = a.getId();
		d.consultationId = a.getConsultationId();
		d.senderId = a.getSenderId();
		d.senderRole = a.getSenderRole().name();
		d.fileName = a.getOriginalName();
		d.mimeType = a.getMimeType();
		d.sizeBytes = a.getSizeBytes();
		d.sha256 = a.getSha256();
		d.createdAt = a.getCreatedAt();
		return d;
	}

	public long getId() {
		return id;
	}

	public int getConsultationId() {
		return consultationId;
	}

	public String getSenderId() {
		return senderId;
	}

	public String getSenderRole() {
		return senderRole;
	}

	public String getFileName() {
		return fileName;
	}

	public String getMimeType() {
		return mimeType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getSha256() {
		return sha256;
	}

	public long getCreatedAt() {
		return createdAt;
	}
}
