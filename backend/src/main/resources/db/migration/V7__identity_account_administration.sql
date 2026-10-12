-- Module: identity. Account administration.
--   * status replaces the enabled flag: ACTIVE, DISABLED (switched off by an administrator, data kept) or LOCKED
--     (blocked for security reasons until an administrator unlocks it). Existing disabled accounts stay disabled.
--   * password_change_required forces a new password at the next sign-in (temporary passwords set by administrators).
--   * last_login_at is shown in the admin console.
-- Roles stay USER/ADMIN; finer-grained administrative roles would extend ck_users_role in a later migration.

ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
UPDATE users SET status = 'DISABLED' WHERE NOT enabled;
ALTER TABLE users DROP COLUMN enabled;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'));

ALTER TABLE users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;

-- Admin statistics and the "at least one active administrator" safeguard.
CREATE INDEX ix_users_role_status ON users (role, status);

-- Sessions also end when a user changes their password or an administrator acts on the account.
ALTER TABLE refresh_tokens DROP CONSTRAINT ck_refresh_tokens_revocation_reason;
ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_revocation_reason
    CHECK (revocation_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'ACCOUNT_DISABLED', 'PASSWORD_CHANGED',
                                 'ADMIN_ACTION'));
