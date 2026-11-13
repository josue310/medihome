package com.teleconsult.service.repository;

import com.teleconsult.model.CallAuditRecord;

/**
 * Persistance du journal d'audit des appels.
 *
 * Interface CDI : l'implémentation active est JdbcCallAuditRepository. Pour
 * passer à JPA, créer par ex. JpaCallAuditRepository (EntityManager +
 * CallAuditRecord annoté @Entity) annotée @Alternative et l'activer dans
 * beans.xml : aucun service n'est à modifier.
 *
 * Les deux méthodes doivent être idempotentes et tolérer l'ordre inverse
 * (fin enregistrée avant le début, en cas de course entre deux threads).
 */
public interface CallAuditRepository {

	/** Enregistre le début effectif d'une communication (CALL_ACCEPT). */
	void recordStart(CallAuditRecord record);

	/** Enregistre la fin d'une communication (HANGUP ou déconnexion brutale). */
	void recordEnd(CallAuditRecord record);
}
