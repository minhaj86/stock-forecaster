# AGENTS.md — Stock Forecast Platform (StockLens)

Guidance for AI coding agents working in this repository. The closest `AGENTS.md` wins: see
[`backend/AGENTS.md`](backend/AGENTS.md) and [`frontend/AGENTS.md`](frontend/AGENTS.md) for stack-specific rules.
Product and architecture context: [`README.md`](README.md), [`docs/architecture.md`](docs/architecture.md).

## What this is

A full-stack **stock analytics, watchlist, forecasting and grounded-AI** platform for educational use, not
investment advice.

- **Backend**: Java 25, Spring Boot 4.1 **modular monolith** (Spring Modulith-verified), PostgreSQL 18 + Flyway,
  Redis 8 cache, ShedLock scheduling.
- **Frontend**: React 19, TypeScript 6, Vite 8, MUI 9, TanStack Query 5, React Router 8, ECharts 6.
- **Status**: backend phases 1–4 (auth, stocks & watchlists, market data, analytics), forecasting phases 1–3
  (naive, moving average, linear regression, exponential smoothing; calibrated ranges; walk-forward backtests) with
  the forecast UI, and frontend phases 1–3 (shell, auth, dashboard, watchlists, search) are done. Next: forecast
  model comparison & auto selection, Monte Carlo, full stock charts, comparison, AI assistant, a licensed
  market-data provider, and rate limiting. Compare and AI Assistant pages are deliberately "Coming soon".

## Repository map

```text
backend/        Spring Boot app (Maven wrapper). Modules under src/main/java/com/stockforecast/:
                shared · identity · user · stock · watchlist · marketdata · quant · analytics · forecasting
frontend/       React SPA (Vite). src/{app,api,features,components,hooks,models,theme,utils,test}
docs/           architecture.md: design, trade-offs and roadmap (the source of truth for intended design)
docker-compose.yml   PostgreSQL, Redis, backend, frontend (nginx); copy .env.example to .env first
.github/        CI workflows, Copilot instructions, agent skills (.github/skills) and custom agents (.github/agents)
```

## Commands (verified)

| Task | Command |
|---|---|
| Backend unit + architecture tests (no Docker) | `cd backend && ./mvnw test` |
| Backend full suite (+ Testcontainers PostgreSQL/Redis) | `cd backend && ./mvnw verify` |
| One backend unit test | `./mvnw test -Dtest=PriceSeriesTest` |
| One backend integration test | `./mvnw verify -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=StockApiIT` |
| Run backend locally | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (needs `docker compose up -d postgres redis`) |
| Frontend install / dev server | `cd frontend && npm ci && npm run dev` (http://localhost:5173, proxies `/api` to :8080) |
| Frontend checks | `npm run lint && npm run typecheck && npm test && npm run build` |
| One frontend test file | `npx vitest run src/features/dashboard/DashboardPage.test.tsx` |
| Whole stack | `cp .env.example .env && docker compose up -d --build` → http://localhost:3000 |
| Reset local data | `docker compose down -v` |
| Everything (helper) | `.github/skills/verify-changes/scripts/verify.sh` (add `--quick` to skip container-backed tests) |

Environment notes:

- **JDK 25** is required (the Maven enforcer rejects older versions). If `mvn`/`java` default to another JDK, set
  `JAVA_HOME` (macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`).
- **Node 22+** (24 LTS recommended). npm 11 blocks install scripts by default; nothing here needs them.
- **Podman instead of Docker** (common on this team): set
  `DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')` and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` before running integration tests or Compose.
- Local ports: PostgreSQL 5433, Redis 6379, backend 8080, frontend 3000 (Compose) or 5173 (Vite).

## Golden rules

1. **A feature is done only when it is done end to end**: backend implementation and tests, frontend connected to
   the real endpoint with loading, empty and error states and tests, both builds green. Never ship an endpoint
   without its UI, or UI against an invented contract. See *Definition of Done* below.
2. **Never invent or recompute financial numbers.** Market data comes from the market-data module, metrics from the
   analytics module, forecasts from the forecasting module (always with ranges, backtest and disclaimer). The
   frontend formats backend values and never derives conflicting figures. LLM features (later) may only explain
   numbers the backend computed. Synthetic data is always labelled (`synthetic: true` →
   "Demo data" banner). Keep the disclaimer: *"Forecasts and AI-generated analysis are informational only and should
   not be considered financial advice."*
3. **Ownership comes from the authenticated principal**, never from ids sent by the client. Another user's resource
   answers **404** (indistinguishable from missing). Every user-scoped feature needs an "user A cannot access user
   B's …" test.
4. **Respect module boundaries and layering** (enforced by `ModularityTest` and `LayeredArchitectureTest`). Modules
   only use each other's top-level package (published API); `domain` stays free of Spring, servlet and Jackson.
5. **Flyway migrations are immutable** once committed. Add a new `V<n>__<module>_<change>.sql`; never edit or
   reorder existing ones.
6. **No secrets in the repository.** Configuration comes from environment variables (`.env` is gitignored;
   `.env.example` documents everything). Never log passwords, tokens, cookies or API keys; mask them in `toString()`.
7. **No real external APIs in automated tests** (market data, LLMs). Use MSW (frontend), MockMvc and Testcontainers
   (backend) and fakes or WireMock for provider adapters.
8. **Keep docs truthful**: update `README.md`, `docs/architecture.md` and these agent files when behaviour, commands
   or conventions change.

## Shared API contract (backend ↔ frontend)

- Base path `/api/v1`. JSON. Instants are ISO-8601 UTC (`2026-10-11T13:42:00Z`), calendar dates `yyyy-MM-dd`.
- **Ratios are decimal fractions** (`0.052` = 5.2 %); prices are decimal numbers in the stock's currency; metrics
  are `null` when history is too short. Never multiply by 100 on the backend.
- Errors always use the envelope
  `{timestamp, status, code, message, path, correlationId, errors?: [{field, message}]}` with a stable machine
  `code` (e.g. `WATCHLIST_NOT_FOUND`, `VALIDATION_FAILED`). The frontend maps it to `ApiError`
  (`frontend/src/api/apiError.ts`).
- Data availability is explicit: `dataStatus` = `READY | PENDING | NOT_TRACKED | UNAVAILABLE` (analytics) or
  `READY | PENDING | NOT_TRACKED | FAILED` (prices). The UI shows a state for each.
- TypeScript types in `frontend/src/models/*` mirror the backend DTOs exactly. Change both sides together.
- Auth: short-lived JWT access token in the response body (kept in memory by the SPA) plus a rotating refresh token
  in an `HttpOnly; SameSite=Strict` cookie scoped to `/api/v1/auth`.

## Definition of Done

- [ ] Backend: domain, application, api and (if needed) migration implemented in the owning module, plus:
      unit tests for rules and math, `*IT` integration tests for the endpoint (including validation errors and
      ownership), architecture tests still green.
- [ ] OpenAPI annotations on new endpoints (`@Operation`, `@Tag`), and error codes documented in the exception.
- [ ] Frontend: types in `models/`, API function in `api/`, query keys in `api/queryKeys.ts`, hooks in the feature,
      UI with loading (skeleton), empty, error (with retry) and, where relevant, pending states.
- [ ] Frontend tests with MSW for the happy path and at least one error or empty path.
- [ ] Accessibility: labelled controls, keyboard reachable, changes shown with arrow + sign (not colour alone).
- [ ] `./mvnw verify` and `npm run lint && npm run typecheck && npm test && npm run build` pass.
- [ ] Docs updated (README tables, `docs/architecture.md` status, agent files if conventions changed).

## Anti-patterns (rejected in review)

- Business logic in controllers or React components; "god" services; static helper grab-bags.
- Returning JPA entities from controllers; `catch (Exception)`; empty catch blocks; `RuntimeException` for business
  failures (use a `DomainException` subclass with a code).
- Accepting `userId` in paths or bodies for user-scoped data.
- Calling a market-data provider (or any slow external system) during a user request.
- Raw Axios calls inside components; server data in global client state; `any` in TypeScript.
- Placeholder `TODO` implementations, unless the feature is explicitly scheduled for a later phase (then say so in
  the UI and docs).

## Agent resources

| File | Purpose |
|---|---|
| `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md` | This guidance (closest file wins) |
| `.github/copilot-instructions.md` | Repository-wide Copilot instructions (also used by Copilot code review) |
| `.github/instructions/*.instructions.md` | Path-scoped rules: Flyway migrations, backend Java, frontend TypeScript |
| `CLAUDE.md` | Imports this file for Claude Code |
| `.github/skills/<name>/SKILL.md` | On-demand workflows (below) |
| `.github/agents/<name>.agent.md` | Specialist agent profiles (below) |

Skills: `full-stack-feature` (Definition of Done workflow), `backend-feature`, `frontend-feature`,
`analytics-metric` (golden-value discipline), `forecast-model` (model SPI, leakage-free backtests),
`market-data-provider` (vendor adapters), `verify-changes`
(`scripts/verify.sh`), `local-stack` (Compose, `scripts/smoke-test.sh`).

Custom agents: `backend-engineer`, `frontend-engineer`, `test-engineer` (implementers); `architecture-reviewer`,
`security-reviewer` (read-only reviewers).
