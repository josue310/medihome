package com.teleconsult.service;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.model.ChatMessage;
import com.teleconsult.model.ChatNotificationEvent;
import com.teleconsult.service.repository.ChatMessageRepository;

/**
 * Chat de consultation : enregistre le message puis déclenche l'événement CDI
 * ChatNotificationEvent, relayé en CHAT_NOTIFY aux participants connectés.
 * Le contenu est stocké brut : c'est au client de l'afficher comme du TEXTE
 * (textContent, jamais innerHTML) pour éviter tout XSS.
 */
@ApplicationScoped
public class ChatService {

	@Inject
	private ChatMessageRepository repository;

	@Inject
	private TeleconsultationConfig config;

	@Inject
	private Event<ChatNotificationEvent> notifications;

	public ChatMessage postMessage(AuthenticatedParticipant sender, String rawContent) {
		String content = sanitize(rawContent);
		if (content.isEmpty()) {
			throw TeleconsultationException.badRequest("EMPTY_MESSAGE", "Message content is required");
		}
		int max = config.getMaxChatMessageLength();
		if (content.length() > max) {
			throw TeleconsultationException.badRequest("MESSAGE_TOO_LONG", "Message exceeds " + max + " characters");
		}

		ChatMessage saved = repository.save(new ChatMessage(sender.getConsultationId(), sender.getUserId(),
				sender.getRole(), content, System.currentTimeMillis()));

		Map<String, Object> payload = new LinkedHashMap<String, Object>();
		payload.put("kind", "MESSAGE");
		payload.put("messageId", saved.getId());
		payload.put("senderRole", saved.getSenderRole().name());
		payload.put("content", saved.getContent());
		payload.put("createdAt", saved.getCreatedAt());
		notifications.fire(new ChatNotificationEvent(sender.getConsultationId(), sender.getUserId(), payload));
		return saved;
	}

	/** Retire les caractères de contrôle (sauf saut de ligne et tabulation) et les espaces en bord. */
	static String sanitize(String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char ch = raw.charAt(i);
			if (ch == '\n' || ch == '\t' || !Character.isISOControl(ch)) {
				sb.append(ch);
			}
		}
		return sb.toString().trim();
	}
}
