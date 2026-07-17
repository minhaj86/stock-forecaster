-- Module: marketdata. Daily OHLCV bars for watched stocks, synchronisation state and the ShedLock table.

CREATE TABLE market_prices
(
    stock_id       UUID           NOT NULL REFERENCES stocks (id),
    bar_date       DATE           NOT NULL,
    open           NUMERIC(19, 6) NOT NULL CHECK (open > 0),
    high           NUMERIC(19, 6) NOT NULL CHECK (high > 0),
    low            NUMERIC(19, 6) NOT NULL CHECK (low > 0),
    close          NUMERIC(19, 6) NOT NULL CHECK (close > 0),
    adjusted_close NUMERIC(19, 6) NOT NULL CHECK (adjusted_close > 0),
    volume         BIGINT         NOT NULL CHECK (volume >= 0),
    data_provider  VARCHAR(32)    NOT NULL,
    ingested_at    TIMESTAMPTZ    NOT NULL,
    -- Natural key: idempotent upserts and efficient per-stock time-range scans.
    PRIMARY KEY (stock_id, bar_date)
);

CREATE TABLE market_data_sync_status
(
    stock_id             UUID PRIMARY KEY REFERENCES stocks (id),
    provider             VARCHAR(32),
    last_bar_date        DATE,
    last_success_at      TIMESTAMPTZ,
    last_attempt_at      TIMESTAMPTZ,
    -- Lease: set while an instance synchronises the stock; stale leases are taken over.
    sync_started_at      TIMESTAMPTZ,
    consecutive_failures INT          NOT NULL DEFAULT 0,
    last_error           VARCHAR(500),
    next_attempt_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL
);

-- ShedLock: one instance runs each scheduled job (https://github.com/lukas-krecan/ShedLock).
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
