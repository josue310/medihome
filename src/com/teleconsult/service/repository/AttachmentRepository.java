package com.teleconsult.service.repository;

import com.teleconsult.model.Attachment;

/** Persistance des métadonnées des documents déposés (interface CDI, prête pour JPA). */
public interface AttachmentRepository {

	/** Enregistre les métadonnées et retourne l'objet complété de son identifiant. */
	Attachment save(Attachment attachment);

	/** Document d'une consultation donnée (null si absent ou rattaché à une autre consultation). */
	Attachment findById(int consultationId, long attachmentId);
}
