-- Module: identity. Accounts and rotating refresh tokens.

CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    -- The application normalises e-mails; the database guarantees it so the unique constraint is case-insensitive.
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Admin statistics ("registered in the last N days").
CREATE INDEX ix_users_created_at ON users (created_at);

CREATE TABLE refresh_tokens
(
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id          UUID        NOT NULL,
    -- Hex SHA-256 of the opaque token; the raw token is never stored.
    token_hash         VARCHAR(64) NOT NULL,
    issued_at          TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    session_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at         TIMESTAMPTZ,
    revocation_reason  VARCHAR(32),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at AND expires_at <= session_expires_at),
    CONSTRAINT ck_refresh_tokens_revocation CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL)),
    CONSTRAINT ck_refresh_tokens_revocation_reason
        CHECK (revocation_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'ACCOUNT_DISABLED'))
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
-- Session-wide revocation (logout, replay detection) only touches still-active tokens.
CREATE INDEX ix_refresh_tokens_active_family ON refresh_tokens (family_id) WHERE revoked_at IS NULL;
-- Housekeeping of expired tokens.
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);
