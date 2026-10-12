# Stock Forecast Platform (StockLens)

A full-stack platform for **stock-market analytics, statistical forecasting, personal watchlists and grounded AI
comparisons**. Users maintain their own watchlists and a personalised dashboard; the backend collects market data
for watched stocks in the background, computes analytics from it and produces statistical forecasts with
uncertainty ranges and walk-forward backtests. A later phase adds an AI analyst whose numerical claims are grounded
in data the platform computed itself.

> **Disclaimer:** Forecasts and AI-generated analysis are informational only and should not be considered
> financial advice. This is an analytics and educational platform, not an investment-advice service.

| | |
|---|---|
| **Status** | Backend phases 1–4 (foundation, stocks & watchlists, market data, analytics), forecasting phases 1–3 (models, calibrated ranges, walk-forward backtesting) with the forecast UI, frontend phases 1–3 (app shell, authentication, dashboard, watchlists, search) and the [admin console](#admin-console) (users, stocks, market-data jobs, Twelve Data provider, audit log) — see [Roadmap](#roadmap) |
| **Backend** | Java 25 · Spring Boot 4.1 (modular monolith) · PostgreSQL 18 · Redis 8 · Flyway |
| **Frontend** | React 19 · TypeScript 6 · Vite 8 · MUI 9 · TanStack Query · React Router · ECharts 6 |
| **Design** | [docs/architecture.md](docs/architecture.md) — architecture & implementation plan |

---

## Contents

- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Repository layout](#repository-layout)
- [Run it with Docker](#run-it-with-docker)
- [Local development](#local-development)
- [Database](#database)
- [Configuration & environment variables](#configuration--environment-variables)
- [Market-data provider configuration](#market-data-provider-configuration)
- [Admin console](#admin-console)
- [LLM provider configuration](#llm-provider-configuration)
- [Authentication](#authentication)
- [API overview & examples](#api-overview--examples)
- [Forecasting](#forecasting)
- [Frontend](#frontend)
- [Testing](#testing)
- [Observability](#observability)
- [Screenshots](#screenshots)
- [Known limitations](#known-limitations)
- [AI agent guidance](#ai-agent-guidance)
- [Roadmap](#roadmap)

---

## Architecture

The backend is a **modular monolith**: one deployable split into business modules (`identity`, `user`, `stock`,
`watchlist`, `marketdata`, `analytics`, `forecasting`, `admin` (the back office), later `ai` and `notification`, plus
a small `shared` kernel and a pure `quant` math library shared by analytics and forecasting).
Each module owns its domain model, use cases, persistence and API, publishes a narrow API from its top-level
package, and may not touch other modules' internals. Boundaries and layering are **enforced by tests** (Spring
Modulith + ArchUnit), so modules can be extracted later if scale demands it.

```mermaid
flowchart LR
    UI["React SPA"] --> EDGE["nginx (/api proxy)"] --> SEC["Spring Security<br/>JWT · CORS · correlation id"]

    subgraph APP["Spring Boot modular monolith"]
        SEC --> ID[identity] & USR[user] & STK[stock] & WL[watchlist] & AN[analytics] & FC[forecasting] & AI[ai]
        WL --> STK
        WL -- StockAddedToWatchlist --> MD[marketdata]
        JOBS["Schedulers + ShedLock"] --> MD
        AN --> MD
        FC --> MD & STK
        AN & FC --> QT["quant (pure math)"]
        AI --> AN & FC & WL & STK
    end

    MD --> MDP[["Market-data provider"]]
    AI --> LLM[["LLM provider"]]
    APP --> PG[("PostgreSQL")]
    APP -.-> RD[("Redis")]
    APP -.-> OBS["Prometheus · Grafana · OTel"]
```

Key principles:

* **No synchronous provider calls on user requests.** Market data flows provider → background ingestion →
  PostgreSQL → (Redis-cached) analytics → API → dashboard.
* **The frontend never computes financial figures** the backend already provides; it formats them.
* **Grounded AI** (later phase): numbers come from the analytics/forecasting modules, the model only explains them.
* **Ownership from the principal.** No endpoint accepts a user id for user-scoped data; another user's watchlist is
  indistinguishable from a non-existent one (404).
* **Dependency inversion at every external edge.** Market-data and LLM vendors sit behind ports
  (`MarketDataProvider`, `LlmProvider`); vendor types never leave their adapters.

Details, trade-offs, schema, API, ingestion, analytics, forecasting and LLM designs:
[docs/architecture.md](docs/architecture.md).

## Technology stack

| Area | Choice |
|---|---|
| Backend runtime | Java 25 (LTS), Spring Boot 4.1 (Framework 7, Security 7, Hibernate 7, Jackson 3), virtual threads |
| Modularity | Spring Modulith (boundary verification, module docs), ArchUnit (layering and coding rules) |
| Security | Spring Security OAuth2 resource server (JWT), Nimbus JOSE (HS256), BCrypt, HttpOnly refresh cookie |
| Persistence | PostgreSQL 18, Spring Data JPA, JDBC for time series, Flyway |
| Numerics | Hipparchus 4 (regression, optimisation, distributions) for forecasting |
| Caching & jobs | Redis 8 (typed JSON caches), ShedLock (one instance per scheduled job) |
| API docs | springdoc-openapi (OpenAPI 3, Swagger UI) |
| Observability | Spring Boot Actuator, Micrometer + Prometheus, structured (ECS) logs, correlation ids |
| Frontend | React 19, TypeScript 6, Vite 8, MUI 9, TanStack Query 5, React Router 8, Axios, React Hook Form + Zod 4, Apache ECharts 6 |
| Testing | JUnit 6, AssertJ, Mockito, Spring Boot Test, Testcontainers · Vitest 5, Testing Library, MSW 3 |
| Delivery | Maven wrapper, Docker multi-stage images (JRE 25, unprivileged nginx), Docker Compose, GitHub Actions |

TypeScript is pinned to 6.0: the native TypeScript 7 compiler is not yet supported by `typescript-eslint`.

## Repository layout

```text
.
├── backend/                 Spring Boot application (Maven wrapper, Dockerfile)
│   └── src/main/java/com/stockforecast/{shared,identity,user,stock,watchlist,marketdata,quant,analytics,forecasting}
├── frontend/                React application (Vite, Dockerfile + nginx config)
│   └── src/{app,api,features,components,hooks,models,theme,utils,test}
├── docs/architecture.md     Architecture & implementation plan
├── docker-compose.yml       PostgreSQL, Redis, backend, frontend
├── .env.example             Documented configuration template
├── .github/workflows/       CI (backend and frontend)
└── pom.xml                  Aggregator so IDEs import the backend from the root
```

## Run it with Docker

Prerequisites: Docker (or Podman, see below).

```bash
cp .env.example .env                     # local-only defaults; never commit .env
docker compose up -d --build             # PostgreSQL, Redis, backend, frontend
```

Open <http://localhost:3000>, create an account (it starts with an empty "My Watchlist"), search for a stock such
as `MSFT` and add it — market data appears on the dashboard within seconds. The API is also reachable directly on
<http://localhost:8080> (Swagger UI: <http://localhost:8080/swagger-ui.html>).

```bash
docker compose ps                        # all services become "healthy"
docker compose logs -f backend
docker compose down                      # add -v to drop the database volume
```

The frontend container serves the static build with unprivileged nginx and proxies `/api` to the backend, so the
browser talks to a single origin (no CORS, and the HttpOnly refresh cookie works). Images: backend on JRE 25 with
layered jars running as a non-root user; frontend built with Node 24 and served by nginx with a strict
Content-Security-Policy. Ports are bound to `127.0.0.1` only.

<details>
<summary>Using Podman instead of Docker</summary>

```bash
podman machine start
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
# Testcontainers: let Ryuk mount the VM's Docker-compatible socket
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
docker compose up -d --build             # or: podman compose up -d --build
```
</details>

## Local development

Prerequisites: **JDK 25**, **Node.js 22+** (24 LTS recommended), Docker/Podman.

```bash
cp .env.example .env
docker compose up -d postgres redis      # infrastructure (PostgreSQL on :5433, Redis on :6379)

cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local      # API on :8080

cd frontend && npm ci && npm run dev     # UI on http://localhost:5173 (proxies /api to :8080)
```

In the `local` profile a random JWT key is generated if `JWT_SECRET` is empty (sessions end on restart), the
refresh cookie is not marked `Secure` (plain HTTP), and the synthetic market-data provider is used. The copied
`.env` creates a dummy local administrator on first start: **`admin@example.com` / `StockLens-Admin-2026`** (see
[first administrator](#the-first-administrator)).

Useful frontend scripts: `npm run dev`, `npm test`, `npm run lint`, `npm run typecheck`, `npm run build`,
`npm run preview`.

## Database

* PostgreSQL 18; schema owned by **Flyway** migrations in `backend/src/main/resources/db/migration`
  (`V<n>__<module>_<change>.sql`). Hibernate only **validates** the schema.
* Each table is owned by exactly one module: `users` (with `status`, `password_change_required`, `last_login_at`),
  `refresh_tokens` (identity) · `user_profiles` (user) · `stocks` (stock, seeded with a reference catalogue of ~70
  US-listed securities; `source` records where an entry's metadata came from) · `watchlists`, `watchlist_items`
  (watchlist) · `market_prices`, `market_data_sync_status`, `market_data_sync_jobs`, `market_price_staging`,
  `market_data_provider_usage`, `shedlock` (marketdata) · `admin_audit_events` (admin).
* Money is `NUMERIC`, timestamps are `TIMESTAMPTZ` (UTC), ids are application-generated UUIDv7; daily bars use the
  natural key `(stock_id, bar_date)` for idempotent upserts and efficient range scans (`market_prices` holds daily
  bars only; intraday data would get its own table keyed by timestamp). Derived values — first/last stored date,
  number of bars, last synchronisation — are computed from these tables rather than duplicated.
* User-owned data is deleted explicitly by each owning module when an account is deleted (`UserAccountDeleted`
  event inside the deletion transaction); the cascading foreign keys remain only as a safety net. Audit entries and
  job history keep no foreign keys so they outlive the accounts they mention.
* Connect locally: `psql "postgresql://stockforecast:change-me-local-only@localhost:5433/stockforecast"`.

## Configuration & environment variables

Profiles: base configuration is **production-safe**; `local` relaxes it for development; `test` is used by
integration tests; `prod` adds JSON logs, an internal management port and disables API docs.

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | – (`local` in Compose) | `local`, `prod` |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `local`: `jdbc:postgresql://localhost:5433/stockforecast`, `stockforecast` / `stockforecast` | PostgreSQL |
| `DATABASE_POOL_SIZE` | `10` | Hikari pool size (bounds DB concurrency under virtual threads) |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / – | Redis cache |
| `CACHE_TYPE` | `redis` | `none` runs without Redis; cache failures never fail requests |
| `MARKET_DATA_PROVIDER` | – (**required**; `synthetic` in `local`/Compose) | Active market-data adapter: `synthetic` or `TWELVE_DATA` |
| `TWELVE_DATA_API_KEY` | – | Twelve Data API key (sent as `Authorization` header, never logged or returned) |
| `TWELVE_DATA_REQUESTS_PER_MINUTE` | `8` | API credits per minute of the Twelve Data plan; sizes the client-side token bucket |
| `TWELVE_DATA_BASE_URL` | `https://api.twelvedata.com` | Override for proxies or tests |
| `MARKET_DATA_INITIAL_HISTORY_YEARS` | `5` | History loaded when a stock is first tracked |
| `MARKET_DATA_SYNC_CRON` | `0 30 22 * * MON-FRI` (UTC) | Daily synchronisation of watched stocks (`-` disables it) |
| `MARKET_DATA_JOB_POLL_INTERVAL` | `PT5S` | How often the job worker looks for due jobs (it is also woken up when a job is queued) |
| `JWT_SECRET` | – (**required** outside `local`/`test`) | Base64 HMAC key ≥ 32 bytes: `openssl rand -base64 48` |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `stock-forecast` / `stock-forecast-api` | Token `iss` / `aud` |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Access-token lifetime (1 min – 1 h) |
| `REFRESH_TOKEN_TTL` / `SESSION_MAX_LIFETIME` | `7d` / `30d` | Sliding refresh-token lifetime / absolute session cap |
| `REFRESH_COOKIE_SECURE` | `true` (`false` in `local`) | `Secure` flag of the refresh cookie |
| `CORS_ALLOWED_ORIGINS` | – (`local`: Vite/nginx dev origins) | Comma-separated explicit origins |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | – | Optional bootstrap administrator (see [first administrator](#the-first-administrator)) |
| `ADMIN_REQUIRE_PASSWORD_CHANGE` | `true` | The bootstrap password is one-time: a new one must be chosen at the first sign-in |
| `SERVER_PORT` / `MANAGEMENT_PORT` | `8080` / `8081` (`prod`) | API port / internal actuator port |
| `API_DOCS_ENABLED` | `false` in `prod` | Expose OpenAPI/Swagger UI in production |
| `VITE_API_BASE_URL` (frontend build) | `/api/v1` | API base URL; relative keeps the SPA same-origin (see `frontend/.env.example`) |
| `VITE_DEV_PROXY_TARGET` (frontend dev) | `http://localhost:8080` | Target of the Vite dev proxy |
| `POSTGRES_*`, `*_HOST_PORT` | see `.env.example` | Docker Compose only |

Secrets are only ever read from the environment. Startup fails fast on invalid configuration (missing or short
JWT key, missing market-data provider, wildcard CORS origin with credentials, inconsistent forecast settings).
Forecast settings live under `app.forecast` in `application.yml` (see [Forecasting](#forecasting)).

## Market-data provider configuration

The application depends only on the `MarketDataProvider` port (`searchStocks`, `getStockMetadata`,
`getHistoricalPrices`, `getLatestQuote`, `checkHealth`, plus `info()` and `capabilities()`); vendor payloads are
normalised inside `marketdata/infrastructure/provider/<vendor>/` and never leave it. `MARKET_DATA_PROVIDER` selects the
adapter (case-insensitive; `twelve-data` and `TWELVE_DATA` are equivalent):

* **`synthetic`** — deterministic simulated end-of-day prices for development and demos without a data subscription.
  The data is **not real**: the provider reports `synthetic: true` (`GET /api/v1/market-data/provider`) and the UI
  shows a "Demo data" banner on every page. It cannot search or describe securities, so administrators register
  stocks manually while it is active.
* **`TWELVE_DATA`** — licensed data from [Twelve Data](https://twelvedata.com/docs) (symbol search, listing metadata,
  daily time series, quotes, plan usage). Set `TWELVE_DATA_API_KEY` and `TWELVE_DATA_REQUESTS_PER_MINUTE` (your plan's
  API credits per minute; Basic = 8):

  ```yaml
  app:
    market-data:
      provider: ${MARKET_DATA_PROVIDER}          # TWELVE_DATA
      history-years: 5                           # MARKET_DATA_INITIAL_HISTORY_YEARS
      twelve-data:
        base-url: https://api.twelvedata.com
        api-key: ${TWELVE_DATA_API_KEY}          # never commit it; never logged or returned by the API
        requests-per-minute: 8
  ```

  A missing key does not stop the application: provider health reports `MISCONFIGURED` and jobs fail with
  `PROVIDER_NOT_CONFIGURED` until it is set. HTML scraping (Google/Yahoo Finance) is deliberately not supported; any
  further vendor (e.g. Alpha Vantage) is another adapter behind the same port.

**Price adjustment** (identical for every adapter): `open`/`high`/`low`/`close` are adjusted for **splits** only
(continuous trade prices for charts); `adjusted_close` is adjusted for **splits and dividends** (a total-return series).
Analytics and forecasting use `adjusted_close`. Twelve Data delivers this by requesting each range twice
(`adjust=splits` and `adjust=all`, one API credit each). When a provider re-adjusts history (new dividend or split),
incremental updates notice it in their correction window and recommend a re-population.

**Synchronisation** always runs as asynchronous **jobs** executed by one worker (cluster-wide ShedLock lock) within
the provider's rate limit — user requests never call the provider:

* *Watching a stock* without stored data queues an `INITIAL_LOAD` (`history-years` of daily bars) in the same
  transaction as the watchlist change; the user never waits for the download.
* *Scheduled sync* (`MARKET_DATA_SYNC_CRON`, plus a catch-up at start-up) queues an incremental `AUTOMATIC_SYNC` for
  every watched, active stock: only bars after the newest stored one are requested (with a 5-day correction window).
* *Administrators* queue latest-data updates, backfills, re-populations and bulk loads (see [Admin console](#admin-console)).

All three paths share the same synchronisation code (`MarketDataSynchronizationService`). **Rate limits**: a token
bucket sized from the plan makes every vendor request wait for a permit; a `429` (with `Retry-After`, or until the
next UTC day for daily quotas) pauses the bucket and re-queues the job without using an attempt. **Retries**: timeouts,
`429` and `5xx` are retried automatically with exponential backoff and jitter (1 min doubling up to 1 h, 5 attempts);
invalid credentials, unknown symbols, rejected requests and malformed or invalid data are not. **Validation**: every
response is checked before anything is stored (instrument, currency, duplicate dates, positive prices,
`high ≥ open/close/low`, `low ≤ open/close`, volume, dates inside the range and not in the future); invalid bars are
skipped and counted, an inconsistent response is rejected as a whole.

## Admin console

The admin console (`/app/admin`, visible in the sidebar for administrators only) is the operational back office:

| Page | What it does |
|---|---|
| **Overview** | users (total, active, disabled, locked, administrators, new this week), stocks (catalogue, tracked, with/without data), market data (provider health, last successful and failed sync), jobs (queued, running, completed and failed today); "Trigger synchronization" queues updates for all watched stocks |
| **Users** | search by e-mail or name, filter by role/status/creation date, paginated; create users with a temporary password (generated securely in the browser) that must be changed at the first sign-in; edit name, e-mail, role and status; disable/enable (data kept, sessions ended), lock, reset password, delete (typed `DELETE` confirmation; removes account, profile & settings, watchlists and sessions); recent audit activity per user |
| **Stocks** | the whole catalogue (including inactive stocks) with data range, record count, last sync, watchers and running jobs; add stocks by **searching the provider first** (metadata comes from the provider) or manually; activate/deactivate |
| **Market Data** | per stock: provider, data range, records, last synchronisation and error, and the actions *Update latest data*, *Backfill historical data* (range, interval, skip or update existing bars) and *Re-populate all data* (typed `REPOPULATE` confirmation); bulk historical loads for up to 50 stocks |
| **Sync Jobs** | job history with filters and live progress; job details (range, attempts, records fetched/inserted/updated/skipped/failed/deleted, duration, sanitised error); retry failed jobs with transient errors; cancel queued jobs |
| **Providers** | active provider, health (*Check now*), API key *configured/missing/not required* (never the key), requests/failures/rate-limited today, last success/failure, rate-limit status, capabilities |
| **Audit Log** | every administrative change: who, what, when, which entity, details (never secrets), correlation id; filter by action, entity, actor and date |

### Admin authorization

* Every `/api/v1/admin/**` endpoint requires a token with the `ADMIN` role **and** an account that is still an active
  administrator in the database (a demoted or disabled administrator's still-valid token is rejected), plus method
  security on every admin service. Unauthenticated requests get `401`, other users `403 ACCESS_DENIED`; hiding the
  navigation is only a convenience. Tests cover every admin endpoint for both cases.
* Safeguards: administrators cannot delete, disable, lock or demote **themselves**
  (`409 SELF_ADMINISTRATION_NOT_ALLOWED`), and the **last active administrator** cannot be removed
  (`409 LAST_ADMINISTRATOR`; enforced under a row lock, so two administrators demoting each other concurrently cannot
  both succeed).
* Roles are `USER` and `ADMIN`. Finer roles (`SUPER_ADMIN`, `SUPPORT`, `READ_ONLY_ADMIN`) would extend the `Role` enum,
  the `ck_users_role` constraint and the admin access policy; they are deliberately not modelled yet.

### The first administrator

The application has no built-in administrator; one is bootstrapped from the environment on the first start.

> **Local development only:** `.env.example` pre-fills a dummy administrator (`admin@example.com` /
> `StockLens-Admin-2026`, `ADMIN_REQUIRE_PASSWORD_CHANGE=false`) so a fresh local stack is usable immediately. This
> password is public. Never use these values outside your own machine: clear them, or replace them with your own
> e-mail and a random one-time password, in any shared, staging or production environment.

Anywhere else, set your own values:

```bash
ADMIN_EMAIL=ops@example.com ADMIN_PASSWORD="$(openssl rand -base64 24)"   # e.g. in .env or your secret store
```

The account is created once (existing accounts are never modified, several instances starting together are safe) and
the configured password is a **one-time credential**: signing in with it only allows choosing a new password
(`ADMIN_REQUIRE_PASSWORD_CHANGE=true` by default). Remove the variables after the first start; further
administrators are created or promoted in the console.

### Market-data operations

| Operation | Endpoint | Behaviour |
|---|---|---|
| Update latest data | `POST /admin/stocks/{symbol}/market-data/update` | Incremental: only dates after the newest stored bar (5-day correction window); a stock without data loads its initial history. No confirmation needed; duplicate requests return the queued job (`created: false`) |
| Backfill / populate | `POST /admin/stocks/{symbol}/market-data/backfill` `{fromDate, toDate, interval, existingDataStrategy}` | Downloads the range; `SKIP` (default) keeps stored bars, `UPDATE` overwrites changed ones. "Populate data" is a backfill of a stock without data |
| Re-populate | `POST /admin/stocks/{symbol}/market-data/repopulate` `{fromDate, toDate, interval}` | **FETCH → VALIDATE → STAGE → VERIFY → TRANSACTIONAL REPLACE**: nothing is deleted until the complete, fully valid replacement is staged; the swap is one transaction; only the span the provider actually returned is replaced. If the provider fails, returns invalid data or returns nothing for a range with data, the stored history is untouched |
| Bulk load | `POST /admin/market-data/bulk-load` | One backfill job per stock (≤ 50), executed one after another within the rate limit |
| Synchronise | `POST /admin/market-data/synchronize` | Queues an incremental update for every watched, active stock |

Every request returns `202 Accepted` with the job (`Location: /api/v1/admin/market-data/jobs/{id}`) and is audited;
the UI polls the job. Only `ONE_DAY` bars are stored (other intervals are rejected). When stored history changes,
analytics and forecast caches are invalidated through the data version (time of the last successful sync) and
analytics are recalculated in the background; a re-population therefore also discards cached forecasts and backtests.

Job statuses: `QUEUED → RUNNING → COMPLETED | PARTIALLY_COMPLETED (some bars rejected) | FAILED`, or `CANCELLED`.
Error codes include `PROVIDER_TIMEOUT`, `PROVIDER_RATE_LIMITED`, `PROVIDER_UNAVAILABLE` (retryable) and
`PROVIDER_AUTHENTICATION_FAILED`, `PROVIDER_NOT_CONFIGURED`, `SYMBOL_NOT_FOUND`, `INVALID_PROVIDER_RESPONSE`,
`INVALID_PROVIDER_DATA`, `REPOPULATION_EMPTY_RESULT` (not retryable). Jobs abandoned by a crashed worker are
re-queued after 10 minutes; finished jobs are kept for 90 days.

### Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Provider health `MISCONFIGURED`, jobs fail with `PROVIDER_NOT_CONFIGURED` or `PROVIDER_AUTHENTICATION_FAILED` | `TWELVE_DATA_API_KEY` missing or wrong; set it and restart the backend |
| Jobs wait with "Provider rate limit reached" | The plan's per-minute or daily credits are used up; jobs resume automatically. Lower the load or raise `TWELVE_DATA_REQUESTS_PER_MINUTE` only if your plan allows it |
| Job failed with `SYMBOL_NOT_FOUND` | The provider does not list the symbol on that exchange (check the stock's MIC); fix the stock and start a new job — such jobs are not retryable |
| `PARTIALLY_COMPLETED` | Some bars failed validation (see the job's message); the valid ones were stored |
| `INVALID_PROVIDER_DATA` on a re-population | The replacement must be completely valid; nothing was changed. Retry later or narrow the range |
| Job message recommends a re-population | The provider re-adjusted already stored prices (dividend or split); re-populate the stock's history |
| A job stays `RUNNING` after a crash | It is re-queued automatically once its heartbeat is older than 10 minutes |
| A user cannot sign in after being disabled/locked | Expected (`ACCOUNT_DISABLED` / `ACCOUNT_LOCKED`); enable the account in the console |

## LLM provider configuration

*Implemented in a later phase.* Controllers depend on the vendor-neutral `LlmProvider` port:

```yaml
ai:
  provider: anthropic   # anthropic | openai | gemini
```

with `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` or `GEMINI_API_KEY`. Per-user rate limits and daily token budgets
protect cost. See [architecture §10](docs/architecture.md#10-llm-integration-grounded-ai).

## Authentication

* **Access token**: short-lived (15 min) JWT returned in the response body and kept **in memory** by the SPA (never
  in localStorage), sent as `Authorization: Bearer …`. Validated statelessly (signature, expiry, issuer, audience,
  `typ=at+jwt`).
* **Refresh token**: opaque 256-bit value in an `HttpOnly; SameSite=Strict` cookie scoped to `/api/v1/auth`, stored
  server-side only as a SHA-256 hash. Every refresh **rotates** it; replaying a rotated token revokes the whole
  session. Sessions end after 30 days regardless of activity.
* **Remember me**: unchecked → a browser-session cookie that disappears when the browser closes; the choice is kept
  across rotations.
* **In the SPA**: the session is restored from the cookie on page load; an Axios interceptor refreshes once on a
  401 and retries the request, concurrent requests share a single refresh, browser tabs coordinate through the Web
  Locks API, and an expired session returns the user to the login page with an explanation.
* **Passwords**: BCrypt (cost 12) with an algorithm prefix for future upgrades; 12–72 characters/bytes; login
  failures never reveal whether an account exists. Users change their password on the profile page
  (`POST /auth/password`, authenticated by the session cookie and the current password; all other sessions end).
* **Account status**: `ACTIVE`, `DISABLED` (switched off by an administrator, data kept, can be re-enabled) or `LOCKED`
  (blocked for security reasons). Only active accounts can sign in or refresh; disabling, locking or a password reset
  ends all sessions. An administrator-issued temporary password only allows choosing a new one: until then every API
  call except `GET /users/me` answers `403 PASSWORD_CHANGE_REQUIRED` and the SPA shows the change-password page.

## API overview & examples

| Area | Endpoints (`/api/v1`) |
|---|---|
| Auth | `POST /auth/register` · `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout` · `POST /auth/password` |
| Profile | `GET /users/me` · `PATCH /users/me` |
| Stocks | `GET /stocks/search?q=` · `GET /stocks?sector=&page=&size=` · `GET /stocks/sectors` · `GET /stocks/{symbol}` |
| Watchlists | `GET/POST /watchlists` · `GET/PATCH/DELETE /watchlists/{id}` · `POST /watchlists/{id}/items` · `PATCH/DELETE /watchlists/{id}/items/{symbol}` |
| Market data | `GET /stocks/{symbol}/prices?period=1Y` · `GET /market-data/provider` |
| Analytics | `GET /stocks/{symbol}/analytics` · `GET /analytics/snapshots?symbols=` · `GET /analytics/performance?symbols=&period=` |
| Dashboard | `GET /dashboard?period=1M[&watchlistId=]` |
| Forecasting | `GET /stocks/{symbol}/forecast?model=&target=&horizon=` · `GET /stocks/{symbol}/forecast/backtest` · `GET /stocks/{symbol}/forecast/models` |
| Admin: overview | `GET /admin/dashboard` · `POST /admin/market-data/synchronize` |
| Admin: users | `GET /admin/users?query=&role=&status=&createdFrom=&createdTo=` · `GET /admin/users/statistics` · `GET/PATCH/DELETE /admin/users/{id}` · `POST /admin/users` · `POST /admin/users/{id}/disable` · `/enable` · `/password-reset` |
| Admin: stocks | `GET /admin/stocks?query=&status=` · `GET /admin/stocks/{symbol}` · `GET /admin/stocks/provider-search?query=` · `POST /admin/stocks` · `POST /admin/stocks/{symbol}/activate` · `/deactivate` |
| Admin: market data | `POST /admin/stocks/{symbol}/market-data/update` · `/backfill` · `/repopulate` · `POST /admin/market-data/bulk-load` · `GET /admin/market-data/jobs` · `GET /admin/market-data/jobs/{id}` · `POST /admin/market-data/jobs/{id}/retry` · `/cancel` |
| Admin: providers & audit | `GET /admin/market-data/providers` · `GET /admin/market-data/providers/health?refresh=` · `GET /admin/audit-log?action=&entityType=&entityId=&actor=&from=&to=` |

```bash
API=http://localhost:8080/api/v1
TOKEN=$(curl -s -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"correct-horse-battery-staple","displayName":"Ada"}' \
  $API/auth/register | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

curl -s -H "$AUTH" "$API/stocks/search?q=micro" | jq '.[].symbol'           # "MU", "MSFT", "MSTR", "AMD"
WL=$(curl -s -H "$AUTH" $API/watchlists | jq -r '.[0].id')                   # the starter "My Watchlist"
curl -s -H "$AUTH" -H 'Content-Type: application/json' -d '{"symbol":"MSFT"}' $API/watchlists/$WL/items
curl -s -H "$AUTH" "$API/dashboard?period=6M" | jq '{stockCount, movers, dataAsOf}'
curl -s -H "$AUTH" "$API/stocks/MSFT/analytics" | jq '{price, changePercent, return1Y, volatility30d, rsi14}'
curl -s -H "$AUTH" "$API/stocks/MSFT/forecast?model=EXPONENTIAL_SMOOTHING&horizon=30" | jq '{specification, summary, metrics: .backtest.metrics}'
curl -s -b cookies.txt -c cookies.txt -X POST $API/auth/refresh | jq .expiresIn
```

Ratios are decimal fractions (`0.052` = 5.2 %). Every error uses one envelope, and every response carries an
`X-Correlation-Id` header:

```json
{
  "timestamp": "2026-10-11T13:17:18.564Z",
  "status": 404,
  "code": "WATCHLIST_NOT_FOUND",
  "message": "Watchlist not found",
  "path": "/api/v1/watchlists/0199...",
  "correlationId": "b7cfbf41-ff61-4555-a4cc-dcc060b1d107"
}
```

Validation failures add `"errors": [{"field": "name", "message": "must not be blank"}]`; some errors add structured
`"details"`, e.g. `INSUFFICIENT_FORECAST_HISTORY` (422) carries `requiredObservations`, `availableObservations` and
`dataStatus`.

## Forecasting

Statistical forecasts for any stock with at least 250 trading days of stored history. A forecast is a **median
estimate with an uncertainty range** (80% by default), shown together with the **walk-forward backtest** of exactly
that configuration and a comparison with the naive "last close" baseline. Forecasts are never presented as
predictions; every response and view carries: *"Forecasts are based on historical data and statistical models.
They are informational only and are not financial advice."*

```mermaid
flowchart LR
    BARS[("Stored daily bars")] --> INPUT["ForecastInput<br/>validated adjusted closes"]
    INPUT --> NAIVE[Naive] & MA[Moving average] & LR[Linear regression] & ES[Exponential smoothing]
    INPUT --> FEAT["Feature engineering<br/>trailing-only"]
    FEAT -.-> ML["ML models (planned)<br/>Python provider"]
    MC["Monte Carlo (planned)"] -.-> CAL
    ARIMA["ARIMA (planned)"] -.-> CAL
    NAIVE & MA & LR & ES --> CAL["Uncertainty ranges<br/>from own past errors"]
    CAL --> BT["Walk-forward backtest<br/>vs naive baseline"]
    BT --> EVAL["Model comparison and selection<br/>(next phase)"]
    BT --> API["Forecast API<br/>Redis cache per data version"]
    EVAL -.-> API
    API --> UI["React forecast view"]
    API -.-> AI["LLM explanation (planned)"]
```

### Models

| Model | How it forecasts | Assumes | Settings |
|---|---|---|---|
| Naive | Last close for every future day; the baseline all models must beat | a random walk | – |
| Moving average | Mean of the last *n* closes, as a flat line | prices fluctuate around a slowly moving level | `window` 5–200 (20) |
| Linear regression | Least-squares line through log prices over a lookback, extended (constant growth rate); reports intercept, slope, R², annual trend | the recent trend persists | `lookback` 60–1260 (252) |
| Exponential smoothing | Simple (level), Holt (linear trend) or damped trend on log prices; weights estimated from one-step errors; `AUTO` keeps the lowest AICc | level/trend evolve gradually | `variant` AUTO/SIMPLE/HOLT/DAMPED, `lookback` (504), optional `alpha`/`beta`/`phi` |
| Monte Carlo, ARIMA | Listed as *coming soon*; requests are rejected rather than answered by a stand-in | – | – |

Numerics use [Hipparchus](https://hipparchus.org) 4 (regression, normal quantiles, Brent and BOBYQA optimisers);
exponential smoothing matches statsmodels to 1e-10 on shared test data. Holt-Winters is not offered because daily
prices have no stable seasonal cycle.

### Uncertainty ranges

Textbook regression/ETS intervals assume independent errors and are overconfident on prices. Each model's range is
instead derived from **its own out-of-sample errors**: from each of the last 250 days of the training data it
forecasts ahead using only data up to that day; the root-mean-square log error at step *k* gives `s(k)` and the range
is `median × exp(±z·s(k))` (z = 1.28 for 80%). Ranges therefore widen with the horizon and never go negative. Steps
with too few measured errors are extrapolated with √k (reported as a caveat).

### Backtesting methodology

Walk-forward (expanding window, no random split): 50 forecast origins, one every 5 trading days over the last 250
days whose outcome is known; each origin must have ≥ 250 days of history. At every origin the model is refitted on
the history **up to that day only** — it never receives a later observation, so parameter estimation, range
calibration and features cannot leak the future. Tests prove it: a spy model checks every training cut-off, and
altering future prices leaves all earlier forecasts bit-identical.

| Metric | Meaning |
|---|---|
| MAE / RMSE | mean absolute / root-mean-square error of the horizon-step forecast (price, or return for `RETURN`) |
| MAPE / sMAPE | percentage errors, price target only (returns near zero make them explode) |
| Direction accuracy | correct up/down calls among forecasts that predicted a change; shown next to the share of up moves (the score of always saying "up"); the naive model makes no calls |
| Range coverage | share of outcomes inside the range — compare with the nominal 80% |
| Skill vs naive | `1 − RMSE / RMSE(naive)`; within ±5% is reported as *similar to the baseline* |

The response flags a model that was worse than naive, and ranges whose backtest coverage fell well below nominal.
**Model comparison and automatic selection** (next phase) rank models by backtest RMSE or MAE and label the winner
*best historical backtest performance*, never *best future model*.

### API and configuration

```bash
curl -s -H "$AUTH" "$API/stocks/MSFT/forecast?model=LINEAR_REGRESSION&lookback=126&target=RETURN&horizon=20"
curl -s -H "$AUTH" "$API/stocks/MSFT/forecast/backtest?model=MOVING_AVERAGE&window=50&horizon=10"
curl -s -H "$AUTH" "$API/stocks/MSFT/forecast/models"   # explanations, settings, history status
```

Errors: `INSUFFICIENT_FORECAST_HISTORY` (422 with required/available days), `UNSUPPORTED_FORECAST_MODEL`,
`UNSUPPORTED_FORECAST_TARGET` (only `PRICE` and `RETURN` today), `INVALID_FORECAST_HORIZON` (1–90 trading days),
`INVALID_FORECAST_PARAMETER` (400). Results are cached in Redis keyed by stock, model, target, horizon, settings and
data version, so a new market-data sync makes old forecasts unreachable.

```yaml
app:
  forecast:
    default-model: EXPONENTIAL_SMOOTHING
    default-horizon: 30            # trading days; max-horizon: 90
    confidence-level: 0.8
    minimum-history-days: 250
    interval-calibration-origins: 250
    backtest: { evaluation-window-days: 250, origin-step-days: 5, minimum-origins: 20 }
    exponential-smoothing: { optimize-parameters: true, default-lookback: 504 }
```

### Roadmap: Monte Carlo, AI and machine learning

* **Monte Carlo** (geometric Brownian motion): drift and volatility estimated from daily log returns; a configurable
  number of seeded simulated paths (10,000 by default); only percentiles (P10, P25, median, P75, P90) are kept and
  drawn as probability bands.
* **ARIMA** from an established statistical library (order chosen by AIC/BIC and backtest RMSE), not an in-house
  implementation.
* **AI explanations**: forecast engine → structured result → context builder → LLM. The model is instructed to use
  only the supplied numbers and to explain uncertainty; it never calculates forecasts.
* **Machine learning** (gradient boosting, LSTM/GRU, transformers) behind the `ForecastProvider` port, e.g. a
  `PythonMlForecastProvider` over REST or gRPC, trained on the trailing-only features from `FeatureEngineer` and
  backtested walk-forward like every other model; the API and UI stay unchanged.

## Frontend

```text
frontend/src
├── app/          providers (query client, theme, notifications, auth), router (lazy routes, guards), layout
├── api/          httpClient (interceptors, single-flight refresh), apiError, one module per backend area, queryKeys
├── features/     auth · dashboard · watchlist · stocks · profile · compare · forecast · ai · admin
├── components/   charts (ECharts wrapper) · tables (sortable metrics table → cards on mobile) · forms · common
├── hooks/ models/ theme/ utils/ test/
```

* **Screens**: login, registration, dashboard (greeting, freshness, summary cards, movers, performance chart
  indexed to 100 with period selector/zoom/legend, watchlist table), watchlists (create, rename, delete with
  confirmation), watchlist detail (sortable by symbol, price, daily change, 1M, 1Y, volatility; add via search;
  remove with undo), global debounced search with "add to watchlist", explore by sector, stock details (price,
  change, tracking state, price chart, return/risk/momentum metrics, forecast tab), forecasts (stock picker from
  watchlists or search; model, horizon, target and model settings; chart with history, dashed median and shaded
  range; median and range cards; backtest panel; model explanation; caveats), profile (incl. password change),
  settings, forced password change, and the [admin console](#admin-console) for administrators. Compare and the AI
  assistant are scheduled for later phases and marked "Soon".
* **States**: skeletons while loading, empty states with a next step, errors with the backend's message, a
  reference id and retry, "syncing" states that poll until newly watched stocks have data, and a demo-data banner.
* **Design system**: light/dark/system theme stored in the browser, semantic `positive`/`negative`/`forecast`
  colours, and changes always shown with an arrow and sign (never colour alone).
* **Accessibility**: labelled forms and controls, skip link, keyboard-reachable tables and search (Ctrl/⌘+K),
  `aria-current` navigation, chart descriptions, live status messages.

## Testing

```bash
cd backend && ./mvnw verify    # unit + architecture + Testcontainers (PostgreSQL, Redis) integration tests
cd frontend && npm test        # Vitest + Testing Library + MSW (no real backend, market-data or LLM APIs)
```

| Suite | Examples |
|---|---|
| Backend unit | metric formulas against independently computed golden values (returns, volatility, drawdown, RSI, momentum, rebasing, trailing indicators), forecasting models against numpy/statsmodels (regression, exponential smoothing incl. optimiser), range calibration, backtest metrics, **walk-forward leakage proofs**, feature truncation invariance, session rotation & replay detection, JWT validation, watchlist invariants, synthetic provider determinism, sync backoff, dashboard movers |
| Backend architecture | `ModularityTest` (Spring Modulith boundaries, diagrams in `target/spring-modulith-docs`), `LayeredArchitectureTest` (ArchUnit) |
| Backend integration | auth flows, **user A cannot read/modify/delete user B's watchlists or items**, expired/tampered tokens, invalid & replayed refresh tokens, ranked search, watchlist-triggered initial loads, idempotent scheduled sync, Redis cache round-trips, dashboard, forecast API (every model, targets, invalid symbol/model/target/horizon/parameter, insufficient history, authentication, backtest, catalogue, cached results); **admin**: every admin endpoint answers 401 anonymously and 403 to users, demoted admins lose access immediately, user search/create/edit/disable/lock/reset/delete with data removal, forced password change, self-protection and the last-administrator rule under concurrent demotion, stock registration/deactivation, backfill skip/update counts, latest-data updates, re-population (bogus bars removed, staging cleaned, **forecast cache invalidated**), job filters/retry/cancel, bulk loads, audit trail; **Twelve Data via WireMock end to end**: provider search and metadata registration, partial validation failures, **provider failure keeps stored data** and retry, malformed/duplicate responses rejected, rate-limited jobs resume, health |
| Provider adapter | Twelve Data against WireMock (never the real API): mapping and adjusted closes, unfinished sessions, no-data ranges, 401/404/429 (+`Retry-After`, daily quota)/5xx, timeouts, malformed payloads, HTTP-200 error bodies, chunking, search/metadata mapping, health, missing key; token-bucket throttle |
| Frontend | login & registration validation, protected routes (redirect and return), session restore/expiry, token refresh & single flight, dashboard (data, empty, pending, error + retry, period change), watchlists (create, delete with confirmation, sort, add, remove/undo, not found), debounced search & add-to-watchlist, forecasts (controls → request parameters, planned models disabled, chart option, loading, error + retry, insufficient history, untracked stock, missing backtest), formatting, accessible change indicator, colour mode; **admin**: route protection and admin navigation, overview, user table/filters/create (secure password generator)/edit/disable/delete confirmation, stock table and provider search with manual fallback, backfill dialog, re-population confirmation, job progress/failure/retry/rate-limit messages, provider status without keys, audit log; forced and self-service password change |

## Observability

* Health: `/actuator/health` (+ `/liveness`, `/readiness`; `marketDataProvider` reports the last known provider
  health without calling it and turns the aggregate into `DEGRADED` (HTTP 200) during an outage, because stored data is
  still served); metrics: `/actuator/prometheus` (HTTP, JVM, Hikari, and market data:
  `market_data_sync_jobs_total{type,result}`, `market_data_sync_failures_total{type,code}`,
  `market_data_records_downloaded_total`, `market_data_records_updated_total{operation}`,
  `market_data_sync_duration_seconds`, `market_data_provider_requests_total{endpoint,outcome}`,
  `market_data_provider_errors_total{code}`, `market_data_provider_rate_limit_total{source}`,
  `market_data_last_success`). In `prod` the actuator runs on `MANAGEMENT_PORT` (8081), which must stay on the
  internal network.
* Logs: correlation id in every line (`X-Correlation-Id` is accepted from callers if well-formed, otherwise
  generated); market-data jobs add `jobId`, `stockSymbol`, `provider` and `operation` to the MDC (ECS JSON fields in
  `prod`). Passwords, tokens and API keys are never logged (the Twelve Data key travels in a header, not the URL).

## Screenshots

_Placeholder — screenshots of the dashboard, watchlist and stock pages will be added here._

## Known limitations

* **Market data is synthetic** unless `MARKET_DATA_PROVIDER=TWELVE_DATA` is configured; synthetic data is labelled as
  such everywhere. Intraday data (1D charts) is not available; prices are end-of-day (`ONE_DAY` only).
* The client-side rate limiter is per instance; job execution is serialised cluster-wide, but interactive admin
  lookups (provider search, health check) on several instances could exceed a tiny plan — the provider's `429`
  handling is the backstop. A Redis-backed bucket is the next step.
* A stock's catalogue symbol is also its provider symbol; the same ticker on two exchanges cannot be registered twice
  yet. Sector and industry come from the provider only on plans that include company profiles (otherwise empty).
* No login/registration rate limiting yet (planned with Bucket4j, Redis-backed); no e-mail verification and no
  e-mail-based password reset (administrators set temporary passwords instead).
* Access tokens remain valid until they expire (≤ 15 min) after logout, disabling or a role change for regular API
  calls; refresh tokens are revoked immediately, and admin APIs check the account's current role and status on every
  request. Audit entries record administrative actions only (not reads, not failed attempts).
* Single HS256 signing key without rotation; RS256/ES256 + JWKS is planned when another service must verify tokens.
* The stock catalogue is a seeded reference list of US listings; provider-backed search will extend it.
* Forecasts are univariate statistical models of past prices (no fundamentals, news or cross-asset effects).
  Ranges assume normally distributed log errors, so fat tails can push real coverage below nominal — the backtest
  coverage is shown and flagged. Projected dates skip weekends but not exchange holidays. Backtests use 50
  overlapping forecasts, so their metrics are noisy estimates, and past accuracy does not guarantee future accuracy.

## AI agent guidance

The repository ships instructions for AI coding agents (GitHub Copilot, and any tool that reads `AGENTS.md`):

| Path | What it is |
|---|---|
| [`AGENTS.md`](AGENTS.md), [`backend/AGENTS.md`](backend/AGENTS.md), [`frontend/AGENTS.md`](frontend/AGENTS.md) | Golden rules, verified commands, conventions and pitfalls (the closest file applies) |
| [`.github/copilot-instructions.md`](.github/copilot-instructions.md), [`.github/instructions/`](.github/instructions) | Copilot repository-wide and path-scoped rules (migrations, Java, TypeScript) |
| [`.github/skills/`](.github/skills) | Agent skills: full-stack feature workflow, backend and frontend features, analytics metrics, forecasting models, market-data providers, verification (`verify.sh`), local stack (`smoke-test.sh`) |
| [`.github/agents/`](.github/agents) | Custom agents: backend, frontend and test engineers; architecture and security reviewers (read-only) |

The helper scripts are useful without an agent too:

```bash
.github/skills/verify-changes/scripts/verify.sh [--quick]     # all quality gates
.github/skills/local-stack/scripts/smoke-test.sh [base-url]   # end-to-end API smoke test of a running stack
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| Backend 1 | Foundation: structure, PostgreSQL/Flyway, users, JWT + refresh tokens, security, OpenAPI, Docker, CI | ✅ done |
| Backend 2 | Stock catalogue & search, watchlists, ownership tests | ✅ done |
| Backend 3 | Market data: provider port, synthetic provider, ingestion, ShedLock scheduler, sync status; Twelve Data adapter, rate limiting, retries with backoff, job queue | ✅ done (Redis-backed rate limiting, circuit breaker next) |
| Backend 4 | Analytics: returns, volatility, drawdown, RSI, momentum, performance, dashboard | ✅ done (SMA series & comparison endpoint next) |
| Frontend 1–3 | App shell, theme, auth, dashboard, watchlists, search, add/remove stocks | ✅ done |
| Frontend 4 | Stock details: candlesticks, SMA overlays, volume | next |
| Frontend 5 | Comparison page with normalised chart | planned |
| 6 | Forecasting phases 1–3: model SPI, naive / moving average / linear regression / exponential smoothing, calibrated ranges, walk-forward backtesting and metrics, features, forecast API and UI | ✅ done (model comparison & auto selection, Monte Carlo, ARIMA, ensembles, AI integration next) |
| 7 | AI: `LlmProvider`, grounded comparisons, conversations, cost protection | planned |
| 8 | Hardening: rate limiting, tracing, dashboards, performance | planned |
| Admin 1–2 | Admin authorization (incl. live DB check), routing, shell, overview; user administration with safeguards, forced password change, audit trail | ✅ done |
| Admin 3 | `MarketDataProvider` port v2, Twelve Data adapter, provider health, usage statistics | ✅ done |
| Admin 4–5 | Stock administration (provider search, registration, activation); market-data job queue (`MarketDataJob`) and worker, latest-data updates, backfills, bulk loads | ✅ done |
| Admin 6–8 | Safe re-population (stage/verify/swap), job UI with retry/cancel, provider monitoring, audit log UI, metrics | ✅ done (market-data deletion, Redis-backed limits, finer admin roles next) |

---

**Financial disclaimer:** All market data, analytics, forecasts and AI-generated content are provided for
informational and educational purposes only. They are not investment advice, an offer or a solicitation to buy or
sell any security. Past performance does not guarantee future results. Forecasts and AI-generated analysis are
informational only and should not be considered financial advice.
