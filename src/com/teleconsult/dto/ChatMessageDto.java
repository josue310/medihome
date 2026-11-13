package com.teleconsult.dto;

import com.teleconsult.model.ChatMessage;

/** Représentation JSON d'un message de chat. */
public class ChatMessageDto {

	private long id;
	private int consultationId;
	private String senderId;
	private String senderRole;
	private String content;
	private long createdAt;

	public static ChatMessageDto from(ChatMessage m) {
		ChatMessageDto d = new ChatMessageDto();
		d.id = m.getId();
		d.consultationId = m.getConsultationId();
		d.senderId = m.getSenderId();
		d.senderRole = m.getSenderRole().name();
		d.content = m.getContent();
		d.createdAt = m.getCreatedAt();
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

	public String getContent() {
		return content;
	}

	public long getCreatedAt() {
		return createdAt;
	}
}
