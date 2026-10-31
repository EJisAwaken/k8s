-- ═══════════════════════════════════════════════════════════════════════════
-- V1 — Socle transverse : comptes, sessions, journal de connexion, audit,
-- numérotation des documents.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE users (
    id                       BIGSERIAL PRIMARY KEY,
    email                    VARCHAR(255) NOT NULL,
    password_hash            VARCHAR(255) NOT NULL,
    role                     VARCHAR(20)  NOT NULL,
    full_name                VARCHAR(150) NOT NULL,
    phone                    VARCHAR(50),
    totp_secret              VARCHAR(64),
    totp_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    recovery_codes           TEXT,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    password_changed_at      TIMESTAMPTZ,
    password_change_required BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ,
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'ACHATS', 'MAGASINIER', 'COMMERCIAL', 'COMPTABLE'))
);

CREATE TABLE user_sessions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64)  NOT NULL,
    user_agent         VARCHAR(500),
    ip_address         VARCHAR(100),
    created_at         TIMESTAMPTZ  NOT NULL,
    last_used_at       TIMESTAMPTZ  NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    revoked_at         TIMESTAMPTZ,
    CONSTRAINT uk_user_sessions_hash UNIQUE (refresh_token_hash)
);
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id);

CREATE TABLE revoked_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE connection_logs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT,
    email          VARCHAR(255) NOT NULL,
    ip_address     VARCHAR(100),
    user_agent     VARCHAR(500),
    success        BOOLEAN      NOT NULL,
    failure_reason VARCHAR(200),
    logged_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_connection_logs_email ON connection_logs (lower(email), logged_at DESC);

-- Journal d'audit (cahier §5) : qui, quand, avant/après.
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    entity_name  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(100),
    action       VARCHAR(10)  NOT NULL,
    changes      TEXT,
    performed_by VARCHAR(255),
    performed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_audit_log_action CHECK (action IN ('INSERT', 'UPDATE', 'DELETE'))
);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_name, entity_id, performed_at DESC);
CREATE INDEX idx_audit_log_date ON audit_log (performed_at DESC);

CREATE TABLE document_sequences (
    id             BIGSERIAL PRIMARY KEY,
    prefixe        VARCHAR(10) NOT NULL,
    annee          INTEGER     NOT NULL,
    dernier_numero BIGINT      NOT NULL,
    CONSTRAINT uk_document_sequences UNIQUE (prefixe, annee)
);
