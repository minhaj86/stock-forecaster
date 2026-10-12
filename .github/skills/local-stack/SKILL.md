---
name: local-stack
description: Start, inspect, smoke-test, reset and troubleshoot the local StockLens environment (Docker Compose with PostgreSQL, Redis, backend and frontend, or backend and Vite dev server against Compose infrastructure). Use when asked to run the app, reproduce a bug in the running system, check logs, reset data or get login credentials.
---

# Local stack

## Start

```bash
cp .env.example .env                      # once; local-only values, gitignored
docker compose up -d --build              # postgres :5433, redis :6379, backend :8080, frontend :3000
docker compose ps                         # wait for "healthy"
```

Open http://localhost:3000. A `.env` copied from `.env.example` bootstraps a dummy local administrator on the first
start: `admin@example.com` / `StockLens-Admin-2026` (no forced password change; local only). Regular users register
on the sign-in page ("Create one"). Another administrator: change `ADMIN_EMAIL`/`ADMIN_PASSWORD` in `.env` and run
`docker compose up -d backend` (existing accounts are never modified).

Development loop with hot reload instead of containers for the apps:

```bash
docker compose up -d postgres redis
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local)    # :8080
(cd frontend && npm ci && npm run dev)                                      # :5173, proxies /api to :8080
```

Podman: export `DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')`
first. Plain `docker compose` may be unavailable; `docker-compose` or `podman compose` work the same way.

## Smoke test

```bash
.github/skills/local-stack/scripts/smoke-test.sh                        # through nginx (default :3000)
.github/skills/local-stack/scripts/smoke-test.sh http://localhost:5173  # through the Vite proxy
```

The script registers a throw-away user, adds a stock, waits for the background backfill, checks the dashboard and
analytics, rotates the refresh cookie and verifies logout. Run it after changes to auth, watchlists, market data or
the nginx/Vite configuration.

## Inspect

- Logs: `docker compose logs -f backend` (each line carries the correlation id from the `X-Correlation-Id`
  response header, so match a failing request by its id).
- API docs: http://localhost:8080/swagger-ui.html · health: http://localhost:8080/actuator/health · metrics:
  http://localhost:8080/actuator/prometheus.
- Database: `docker compose exec postgres psql -U stockforecast -d stockforecast` (password from `.env`).
  Useful tables: `users`, `watchlists`, `watchlist_items`, `market_prices`, `market_data_sync_status`.
- Cache: `docker compose exec redis redis-cli --scan --pattern 'sf:v1:*'`.

## Reset and rebuild

- Fresh data: `docker compose down -v && docker compose up -d --build`.
- After backend code changes: `docker compose up -d --build backend`; frontend: `docker compose up -d --build frontend`.
- Market data is **synthetic** (`MARKET_DATA_PROVIDER=synthetic`) and labelled as demo data in the UI. It is
  deterministic per symbol and published like an end-of-day feed (after 21:00 UTC).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Port already in use | Change `POSTGRES_HOST_PORT`, `REDIS_HOST_PORT`, `BACKEND_HOST_PORT` or `FRONTEND_HOST_PORT` in `.env`. |
| Backend exits with "JWT_SECRET must be set" | You run the `prod` profile; Compose uses `local`. Set `SPRING_PROFILES_ACTIVE=local` or provide `JWT_SECRET`. |
| Users are signed out after a backend restart | Expected in `local` without `JWT_SECRET` (ephemeral key). Set `JWT_SECRET=$(openssl rand -base64 48)` in `.env`. |
| A stock stays "Syncing…" | Check `docker compose logs backend` for `Market-data sync failed`, and `market_data_sync_status.last_error`. |
| 401 on `/api/v1/auth/refresh` in the browser console on first load | Expected for anonymous visitors: the SPA tries to restore a session from the HttpOnly cookie. |
