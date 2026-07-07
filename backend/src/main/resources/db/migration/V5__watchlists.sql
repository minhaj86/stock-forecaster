-- Module: watchlist. User-owned watchlists referencing canonical stocks by id.

CREATE TABLE watchlists
(
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);

-- Names are unique per user, case-insensitively.
CREATE UNIQUE INDEX uq_watchlists_user_name ON watchlists (user_id, lower(name));
CREATE INDEX ix_watchlists_user_created ON watchlists (user_id, created_at);

CREATE TABLE watchlist_items
(
    id           UUID PRIMARY KEY,
    watchlist_id UUID        NOT NULL REFERENCES watchlists (id) ON DELETE CASCADE,
    stock_id     UUID        NOT NULL REFERENCES stocks (id),
    added_at     TIMESTAMPTZ NOT NULL,
    notes        VARCHAR(500),
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_watchlist_items_stock UNIQUE (watchlist_id, stock_id)
);

-- "Which stocks are watched by anyone" drives market-data synchronisation.
CREATE INDEX ix_watchlist_items_stock ON watchlist_items (stock_id);
