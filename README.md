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
| **Status** | Backend phases 1–4 (foundation, stocks & watchlists, market data, analytics), forecasting phases 1–3 (models, calibrated ranges, walk-forward backtesting) with the forecast UI, and frontend phases 1–3 (app shell, authentication, dashboard, watchlists, search) — see [Roadmap](#roadmap) |
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
`watchlist`, `marketdata`, `analytics`, `forecasting`, later `ai` and `notification`, plus a small `shared` kernel
and a pure `quant` math library shared by analytics and forecasting).
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
refresh cookie is not marked `Secure` (plain HTTP), and the synthetic market-data provider is used. Set
`ADMIN_EMAIL`/`ADMIN_PASSWORD` to get an administrator account on first start.

Useful frontend scripts: `npm run dev`, `npm test`, `npm run lint`, `npm run typecheck`, `npm run build`,
`npm run preview`.

## Database

* PostgreSQL 18; schema owned by **Flyway** migrations in `backend/src/main/resources/db/migration`
  (`V<n>__<module>_<change>.sql`). Hibernate only **validates** the schema.
* Each table is owned by exactly one module: `users`, `refresh_tokens` (identity) · `user_profiles` (user) ·
  `stocks` (stock, seeded with a reference catalogue of ~70 US-listed securities) · `watchlists`,
  `watchlist_items` (watchlist) · `market_prices`, `market_data_sync_status`, `shedlock` (marketdata).
* Money is `NUMERIC`, timestamps are `TIMESTAMPTZ` (UTC), ids are application-generated UUIDv7; daily bars use the
  natural key `(stock_id, bar_date)` for idempotent upserts and efficient range scans.
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
| `MARKET_DATA_PROVIDER` | – (**required**; `synthetic` in `local`/Compose) | Active market-data adapter |
| `MARKET_DATA_SYNC_CRON` | `0 30 22 * * MON-FRI` (UTC) | Daily synchronisation of watched stocks |
| `JWT_SECRET` | – (**required** outside `local`/`test`) | Base64 HMAC key ≥ 32 bytes: `openssl rand -base64 48` |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `stock-forecast` / `stock-forecast-api` | Token `iss` / `aud` |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Access-token lifetime (1 min – 1 h) |
| `REFRESH_TOKEN_TTL` / `SESSION_MAX_LIFETIME` | `7d` / `30d` | Sliding refresh-token lifetime / absolute session cap |
| `REFRESH_COOKIE_SECURE` | `true` (`false` in `local`) | `Secure` flag of the refresh cookie |
| `CORS_ALLOWED_ORIGINS` | – (`local`: Vite/nginx dev origins) | Comma-separated explicit origins |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | – | Optional bootstrap administrator |
| `SERVER_PORT` / `MANAGEMENT_PORT` | `8080` / `8081` (`prod`) | API port / internal actuator port |
| `API_DOCS_ENABLED` | `false` in `prod` | Expose OpenAPI/Swagger UI in production |
| `VITE_API_BASE_URL` (frontend build) | `/api/v1` | API base URL; relative keeps the SPA same-origin (see `frontend/.env.example`) |
| `VITE_DEV_PROXY_TARGET` (frontend dev) | `http://localhost:8080` | Target of the Vite dev proxy |
| `POSTGRES_*`, `*_HOST_PORT` | see `.env.example` | Docker Compose only |

Secrets are only ever read from the environment. Startup fails fast on invalid configuration (missing or short
JWT key, missing market-data provider, wildcard CORS origin with credentials, inconsistent forecast settings).
Forecast settings live under `app.forecast` in `application.yml` (see [Forecasting](#forecasting)).

## Market-data provider configuration

The application depends only on the `MarketDataProvider` port (`getQuote`, `getHistoricalPrices`, `searchStocks`);
the adapter is chosen with `MARKET_DATA_PROVIDER`.

* **`synthetic` (available now)** — deterministic simulated end-of-day prices for development and demos without a
  data subscription. The data is **not real**: the provider reports `synthetic: true`
  (`GET /api/v1/market-data/provider`) and the UI shows a "Demo data" banner on every page.
* **Licensed providers (next)** — e.g. Twelve Data or Alpha Vantage via `MARKET_DATA_API_KEY`, with rate limiting,
  retries and circuit breaking. See [architecture §7](docs/architecture.md#7-market-data-ingestion).

Only stocks on at least one watchlist are synchronised: adding a stock triggers an immediate background backfill
(5 years of daily bars); a ShedLock-guarded job refreshes all watched stocks daily and catches up after downtime.

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
  failures never reveal whether an account exists.

## API overview & examples

| Area | Endpoints (`/api/v1`) |
|---|---|
| Auth | `POST /auth/register` · `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout` |
| Profile | `GET /users/me` · `PATCH /users/me` |
| Stocks | `GET /stocks/search?q=` · `GET /stocks?sector=&page=&size=` · `GET /stocks/sectors` · `GET /stocks/{symbol}` |
| Watchlists | `GET/POST /watchlists` · `GET/PATCH/DELETE /watchlists/{id}` · `POST /watchlists/{id}/items` · `PATCH/DELETE /watchlists/{id}/items/{symbol}` |
| Market data | `GET /stocks/{symbol}/prices?period=1Y` · `GET /market-data/provider` |
| Analytics | `GET /stocks/{symbol}/analytics` · `GET /analytics/snapshots?symbols=` · `GET /analytics/performance?symbols=&period=` |
| Dashboard | `GET /dashboard?period=1M[&watchlistId=]` |
| Forecasting | `GET /stocks/{symbol}/forecast?model=&target=&horizon=` · `GET /stocks/{symbol}/forecast/backtest` · `GET /stocks/{symbol}/forecast/models` |
| Admin | `GET /admin/users/statistics` |

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
├── features/     auth · dashboard · watchlist · stocks · profile · compare · forecast · ai
├── components/   charts (ECharts wrapper) · tables (sortable metrics table → cards on mobile) · forms · common
├── hooks/ models/ theme/ utils/ test/
```

* **Screens**: login, registration, dashboard (greeting, freshness, summary cards, movers, performance chart
  indexed to 100 with period selector/zoom/legend, watchlist table), watchlists (create, rename, delete with
  confirmation), watchlist detail (sortable by symbol, price, daily change, 1M, 1Y, volatility; add via search;
  remove with undo), global debounced search with "add to watchlist", explore by sector, stock details (price,
  change, tracking state, price chart, return/risk/momentum metrics, forecast tab), forecasts (stock picker from
  watchlists or search; model, horizon, target and model settings; chart with history, dashed median and shaded
  range; median and range cards; backtest panel; model explanation; caveats), profile, settings. Compare and the AI
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
| Backend integration | auth flows, **user A cannot read/modify/delete user B's watchlists or items**, expired/tampered tokens, invalid & replayed refresh tokens, ranked search, event-driven backfill, idempotent sync, Redis cache round-trips, dashboard, forecast API (every model, targets, invalid symbol/model/target/horizon/parameter, insufficient history, authentication, backtest, catalogue, cached results) |
| Frontend | login & registration validation, protected routes (redirect and return), session restore/expiry, token refresh & single flight, dashboard (data, empty, pending, error + retry, period change), watchlists (create, delete with confirmation, sort, add, remove/undo, not found), debounced search & add-to-watchlist, forecasts (controls → request parameters, planned models disabled, chart option, loading, error + retry, insufficient history, untracked stock, missing backtest), formatting, accessible change indicator, colour mode |

## Observability

* Health: `/actuator/health` (+ `/liveness`, `/readiness`); metrics: `/actuator/prometheus` (HTTP, JVM, Hikari,
  `market_data_requests`, `market_data_failures`, `market_data_sync_duration`, `market_data_last_success`, …).
  In `prod` the actuator runs on `MANAGEMENT_PORT` (8081), which must stay on the internal network.
* Logs: correlation id in every line (`X-Correlation-Id` is accepted from callers if well-formed, otherwise
  generated); ECS JSON in `prod`. Passwords, tokens and keys are never logged.

## Screenshots

_Placeholder — screenshots of the dashboard, watchlist and stock pages will be added here._

## Known limitations

* **Market data is synthetic** until a licensed provider adapter is added; it is labelled as such everywhere.
  Intraday data (1D charts) is not available; prices are end-of-day.
* No login/registration rate limiting yet (planned with Bucket4j, Redis-backed); no e-mail verification or password
  reset (the login page explains this).
* Access tokens remain valid until they expire (≤ 15 min) after logout; refresh tokens are revoked immediately.
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
| Backend 3 | Market data: provider port, synthetic provider, ingestion, ShedLock scheduler, sync status | ✅ done (licensed adapter, rate limits, retries/circuit breaker next) |
| Backend 4 | Analytics: returns, volatility, drawdown, RSI, momentum, performance, dashboard | ✅ done (SMA series & comparison endpoint next) |
| Frontend 1–3 | App shell, theme, auth, dashboard, watchlists, search, add/remove stocks | ✅ done |
| Frontend 4 | Stock details: candlesticks, SMA overlays, volume | next |
| Frontend 5 | Comparison page with normalised chart | planned |
| 6 | Forecasting phases 1–3: model SPI, naive / moving average / linear regression / exponential smoothing, calibrated ranges, walk-forward backtesting and metrics, features, forecast API and UI | ✅ done (model comparison & auto selection, Monte Carlo, ARIMA, ensembles, AI integration next) |
| 7 | AI: `LlmProvider`, grounded comparisons, conversations, cost protection | planned |
| 8 | Hardening: rate limiting, tracing, dashboards, performance | planned |

---

**Financial disclaimer:** All market data, analytics, forecasts and AI-generated content are provided for
informational and educational purposes only. They are not investment advice, an offer or a solicitation to buy or
sell any security. Past performance does not guarantee future results. Forecasts and AI-generated analysis are
informational only and should not be considered financial advice.
