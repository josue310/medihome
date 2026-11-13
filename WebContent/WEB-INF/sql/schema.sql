-- Schéma complet de Medi Home (base vide -> application prête).
-- Exécuté automatiquement au démarrage par com.util.DatabaseBootstrap.
-- Idempotent : CREATE TABLE IF NOT EXISTS (les tables existantes ne sont pas modifiées).
-- Chaque table a une clé primaire (exigé par certains MySQL hébergés, ex. Aiven).
-- Une instruction par bloc, séparée par un point-virgule en fin de ligne.

CREATE TABLE IF NOT EXISTS user_dtls (
  user_id   INT NOT NULL AUTO_INCREMENT,
  full_name VARCHAR(225) DEFAULT NULL,
  email     VARCHAR(225) DEFAULT NULL,
  password  VARCHAR(225) DEFAULT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doctor (
  id            INT NOT NULL AUTO_INCREMENT,
  full_name     VARCHAR(225) DEFAULT NULL,
  dob           VARCHAR(225) DEFAULT NULL,
  qualification VARCHAR(225) DEFAULT NULL,
  specialist    VARCHAR(225) DEFAULT NULL,
  email         VARCHAR(225) DEFAULT NULL,
  mobno         VARCHAR(225) DEFAULT NULL,
  password      VARCHAR(225) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS specialist (
  id        INT NOT NULL AUTO_INCREMENT,
  spec_name VARCHAR(225) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointment (
  id           INT NOT NULL AUTO_INCREMENT,
  user_id      INT DEFAULT NULL,
  fullname     VARCHAR(225) DEFAULT NULL,
  gender       VARCHAR(225) DEFAULT NULL,
  appoint_date VARCHAR(255) DEFAULT NULL,
  email        VARCHAR(225) DEFAULT NULL,
  phno         VARCHAR(225) DEFAULT NULL,
  diseases     VARCHAR(225) DEFAULT NULL,
  doctor_id    INT DEFAULT NULL,
  address      VARCHAR(225) DEFAULT NULL,
  status       VARCHAR(225) DEFAULT NULL,
  age          VARCHAR(11) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin (
  id       INT NOT NULL AUTO_INCREMENT,
  email    VARCHAR(225) NOT NULL,
  password VARCHAR(225) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_call_audit (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  call_id          CHAR(36) NOT NULL,
  consultation_id  INT NOT NULL,
  caller_id        VARCHAR(64) NOT NULL,
  callee_id        VARCHAR(64) NOT NULL,
  started_at       DATETIME(3) NULL,
  ended_at         DATETIME(3) NULL,
  duration_seconds DECIMAL(10,3) NULL,
  end_reason       VARCHAR(32) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_call_audit_call (call_id),
  KEY idx_call_audit_consultation (consultation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_message (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  consultation_id INT NOT NULL,
  sender_id       VARCHAR(64) NOT NULL,
  sender_role     VARCHAR(16) NOT NULL,
  content         TEXT NOT NULL,
  created_at      DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_message_consultation (consultation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_attachment (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  consultation_id INT NOT NULL,
  sender_id       VARCHAR(64) NOT NULL,
  sender_role     VARCHAR(16) NOT NULL,
  original_name   VARCHAR(255) NOT NULL,
  stored_name     VARCHAR(64) NOT NULL,
  mime_type       VARCHAR(64) NOT NULL,
  size_bytes      BIGINT NOT NULL,
  sha256          CHAR(64) NOT NULL,
  created_at      DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attachment_stored (stored_name),
  KEY idx_attachment_consultation (consultation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
