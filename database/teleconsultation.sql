-- Module de télé-consultation : tables d'audit, de chat et de documents.
-- Idempotent (peut être rejoué). consultation_id = appointment.id.

CREATE TABLE IF NOT EXISTS consultation_call_audit (
  id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  call_id          CHAR(36)      NOT NULL,
  consultation_id  INT           NOT NULL,
  caller_id        VARCHAR(64)   NOT NULL,
  callee_id        VARCHAR(64)   NOT NULL,
  started_at       DATETIME(3)   NULL COMMENT 'debut effectif (CALL_ACCEPT), horodatage serveur',
  ended_at         DATETIME(3)   NULL COMMENT 'fin (HANGUP ou deconnexion brutale)',
  duration_seconds DECIMAL(10,3) NULL COMMENT 'duree exacte, precision milliseconde',
  end_reason       VARCHAR(32)   NULL COMMENT 'HANGUP | CONNECTION_LOST | SESSION_REPLACED',
  UNIQUE KEY uk_call_audit_call (call_id),
  KEY idx_call_audit_consultation (consultation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_message (
  id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  consultation_id  INT           NOT NULL,
  sender_id        VARCHAR(64)   NOT NULL,
  sender_role      VARCHAR(16)   NOT NULL,
  content          TEXT          NOT NULL,
  created_at       DATETIME(3)   NOT NULL,
  KEY idx_message_consultation (consultation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_attachment (
  id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  consultation_id  INT           NOT NULL,
  sender_id        VARCHAR(64)   NOT NULL,
  sender_role      VARCHAR(16)   NOT NULL,
  original_name    VARCHAR(255)  NOT NULL,
  stored_name      VARCHAR(64)   NOT NULL,
  mime_type        VARCHAR(64)   NOT NULL,
  size_bytes       BIGINT        NOT NULL,
  sha256           CHAR(64)      NOT NULL,
  created_at       DATETIME(3)   NOT NULL,
  UNIQUE KEY uk_attachment_stored (stored_name),
  KEY idx_attachment_consultation (consultation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
