-- Module: user. One profile per account.
-- The foreign key crosses a module boundary on purpose: in a single database, integrity (and cascading account
-- deletion) outweighs schema independence. It must be dropped if the module is ever extracted.

CREATE TABLE user_profiles
(
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    time_zone    VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0
);
