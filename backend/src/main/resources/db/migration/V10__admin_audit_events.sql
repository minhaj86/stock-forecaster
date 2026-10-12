-- Module: admin. Append-only audit trail of administrative actions (the application only inserts).
-- No foreign keys on purpose: entries must outlive the users, stocks and jobs they describe, so the acting
-- administrator's e-mail is captured at the time of the action. metadata never contains secrets.
CREATE TABLE admin_audit_events
(
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor_user_id  UUID         NOT NULL,
    actor_email    VARCHAR(320) NOT NULL,
    action         VARCHAR(64)  NOT NULL,
    entity_type    VARCHAR(32)  NOT NULL,
    entity_id      VARCHAR(64),
    summary        VARCHAR(300) NOT NULL,
    metadata       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    correlation_id VARCHAR(64)
);

-- Audit log page: newest first, optionally filtered by entity, action or actor.
CREATE INDEX ix_admin_audit_events_occurred_at ON admin_audit_events (occurred_at DESC);
CREATE INDEX ix_admin_audit_events_entity ON admin_audit_events (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_admin_audit_events_action ON admin_audit_events (action, occurred_at DESC);
CREATE INDEX ix_admin_audit_events_actor ON admin_audit_events (actor_user_id, occurred_at DESC);
