package com.teleconsult.service.repository;

import com.teleconsult.model.ChatMessage;

/** Persistance des messages du chat de consultation (interface CDI, prête pour JPA). */
public interface ChatMessageRepository {

	/** Enregistre le message et retourne l'objet complété de son identifiant. */
	ChatMessage save(ChatMessage message);
}
