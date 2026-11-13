package com.teleconsult.model;

/** Message texte du chat d'une consultation. */
public class ChatMessage {

	private long id;
	private int consultationId;
	private String senderId;
	private Role senderRole;
	private String content;
	private long createdAt;

	public ChatMessage() {
	}

	public ChatMessage(int consultationId, String senderId, Role senderRole, String content, long createdAt) {
		this.consultationId = consultationId;
		this.senderId = senderId;
		this.senderRole = senderRole;
		this.content = content;
		this.createdAt = createdAt;
	}

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

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}
}
