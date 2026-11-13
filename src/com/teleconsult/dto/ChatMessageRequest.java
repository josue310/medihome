package com.teleconsult.dto;

/** Corps de POST /api/consultations/{id}/messages : { "content": "..." } */
public class ChatMessageRequest {

	private String content;

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
