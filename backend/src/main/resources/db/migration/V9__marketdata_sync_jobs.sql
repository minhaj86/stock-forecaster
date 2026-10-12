-- Module: marketdata. Asynchronous synchronisation jobs, provider usage statistics and the staging area for safe
-- history re-population.

-- One row per job. The scheduler, users watching new stocks and administrators all queue jobs here; a single worker
-- (cluster-wide lock) executes them within the provider's rate limits.
CREATE TABLE market_data_sync_jobs
(
    id                     UUID PRIMARY KEY,
    job_type               VARCHAR(20)  NOT NULL,
    status                 VARCHAR(24)  NOT NULL,
    trigger_source         VARCHAR(16)  NOT NULL,
    -- Cross-module FK to the stock catalogue (integrity in the shared database).
    stock_id               UUID         NOT NULL REFERENCES stocks (id),
    provider               VARCHAR(32)  NOT NULL,
    bar_interval           VARCHAR(16)  NOT NULL,
    existing_data_strategy VARCHAR(10),
    from_date              DATE,
    to_date                DATE,
    -- Administrator who requested the job (no FK: the job history outlives deleted accounts).
    requested_by           UUID,
    requested_at           TIMESTAMPTZ  NOT NULL,
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    heartbeat_at           TIMESTAMPTZ,
    attempts               INT          NOT NULL DEFAULT 0,
    max_attempts           INT          NOT NULL,
    next_attempt_at        TIMESTAMPTZ,
    progress               INT          NOT NULL DEFAULT 0,
    status_message         VARCHAR(300),
    records_fetched        INT          NOT NULL DEFAULT 0,
    records_inserted       INT          NOT NULL DEFAULT 0,
    records_updated        INT          NOT NULL DEFAULT 0,
    records_skipped        INT          NOT NULL DEFAULT 0,
    records_failed         INT          NOT NULL DEFAULT 0,
    records_deleted        INT          NOT NULL DEFAULT 0,
    error_code             VARCHAR(40),
    error_message          VARCHAR(500),
    retryable              BOOLEAN      NOT NULL DEFAULT FALSE,
    retry_of_job_id        UUID REFERENCES market_data_sync_jobs (id) ON DELETE SET NULL,
    batch_id               UUID,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_market_data_sync_jobs_type
        CHECK (job_type IN ('INITIAL_LOAD', 'LATEST_UPDATE', 'BACKFILL', 'REPOPULATE', 'AUTOMATIC_SYNC')),
    CONSTRAINT ck_market_data_sync_jobs_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_market_data_sync_jobs_trigger CHECK (trigger_source IN ('ADMIN', 'WATCHLIST', 'SCHEDULE')),
    CONSTRAINT ck_market_data_sync_jobs_interval CHECK (bar_interval IN ('ONE_DAY')),
    CONSTRAINT ck_market_data_sync_jobs_strategy CHECK (existing_data_strategy IN ('SKIP', 'UPDATE')),
    CONSTRAINT ck_market_data_sync_jobs_range CHECK (from_date IS NULL OR to_date IS NULL OR from_date <= to_date),
    CONSTRAINT ck_market_data_sync_jobs_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_market_data_sync_jobs_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

-- Worker: the next due queued job.
CREATE INDEX ix_market_data_sync_jobs_queue ON market_data_sync_jobs (requested_at) WHERE status = 'QUEUED';
-- "Is there an active job for this stock?" (deduplication, one running job per stock, admin stock list).
CREATE INDEX ix_market_data_sync_jobs_active_stock ON market_data_sync_jobs (stock_id)
    WHERE status IN ('QUEUED', 'RUNNING');
-- Job history, newest first, optionally per stock.
CREATE INDEX ix_market_data_sync_jobs_requested_at ON market_data_sync_jobs (requested_at DESC);
CREATE INDEX ix_market_data_sync_jobs_stock ON market_data_sync_jobs (stock_id, requested_at DESC);
-- Dashboard counts per status and day, retention clean-up.
CREATE INDEX ix_market_data_sync_jobs_status_completed ON market_data_sync_jobs (status, completed_at);

-- Validated provider data of a running re-population. Production rows are only replaced (in one transaction) after
-- the complete replacement has been staged and verified, so a provider failure can never lose stored history.
CREATE TABLE market_price_staging
(
    job_id         UUID           NOT NULL REFERENCES market_data_sync_jobs (id) ON DELETE CASCADE,
    bar_date       DATE           NOT NULL,
    open           NUMERIC(19, 6) NOT NULL CHECK (open > 0),
    high           NUMERIC(19, 6) NOT NULL CHECK (high > 0),
    low            NUMERIC(19, 6) NOT NULL CHECK (low > 0),
    close          NUMERIC(19, 6) NOT NULL CHECK (close > 0),
    adjusted_close NUMERIC(19, 6) NOT NULL CHECK (adjusted_close > 0),
    volume         BIGINT         NOT NULL CHECK (volume >= 0),
    PRIMARY KEY (job_id, bar_date)
);

-- Provider requests per day across all instances (admin console: requests today, failures, last success/failure).
CREATE TABLE market_data_provider_usage
(
    provider             VARCHAR(32) NOT NULL,
    usage_date           DATE        NOT NULL,
    requests             INT         NOT NULL DEFAULT 0,
    failures             INT         NOT NULL DEFAULT 0,
    rate_limited         INT         NOT NULL DEFAULT 0,
    last_success_at      TIMESTAMPTZ,
    last_failure_at      TIMESTAMPTZ,
    last_failure_message VARCHAR(300),
    PRIMARY KEY (provider, usage_date)
);

-- Concurrency and backoff moved to the job table: the per-stock lease and retry time are no longer used.
ALTER TABLE market_data_sync_status DROP COLUMN sync_started_at;
ALTER TABLE market_data_sync_status DROP COLUMN next_attempt_at;
