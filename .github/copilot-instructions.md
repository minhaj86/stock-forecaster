# Copilot instructions — Stock Forecast Platform (StockLens)

Full guidance for agents lives in `AGENTS.md` (root), `backend/AGENTS.md` and `frontend/AGENTS.md`. Read the one
closest to the files you change. Reusable workflows are in `.github/skills/`, specialist agents in `.github/agents/`.

## Project in one paragraph

Educational stock analytics platform (not investment advice). The backend is a Java 25 / Spring Boot 4.1 modular
monolith (`backend/`, modules `shared, identity, user, stock, watchlist, marketdata, quant, analytics, forecasting`) on PostgreSQL 18
(Flyway) and Redis 8. The frontend is a React 19 / TypeScript 6 / MUI 9 / TanStack Query SPA (`frontend/`). Market
data is currently produced by a clearly labelled **synthetic** provider.

## Always

- Deliver features end to end: backend, tests, frontend wired to the real endpoint with loading, empty and error
  states, frontend tests, and docs.
- Derive ownership from the authenticated principal (`@CurrentUser AuthenticatedUser`). Foreign resources → 404.
  Add a "user A cannot access user B's …" test for anything user-scoped.
- Keep module boundaries: use only another module's top-level package (published API). `domain` code has no
  Spring, servlet or Jackson dependencies.
- Business failures: subclass `DomainException` with a stable `code`; never return JPA entities from controllers.
- Financial numbers come from the backend (analytics/market data). The frontend formats them with
  `utils/format.ts` and never recomputes them. Ratios are decimal fractions.
- New schema: a new Flyway migration `V<n>__<module>_<change>.sql`; never edit existing migrations.
- Configuration from environment variables only; never commit secrets or log tokens, passwords or keys.
- Tests never call real market-data or LLM APIs (MSW, MockMvc, Testcontainers, fakes).

## Verify before finishing

```bash
cd backend && ./mvnw verify                  # needs Docker/Podman; ./mvnw test for unit + architecture only
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

## Code review focus

Ownership/authorisation leaks, module-boundary or layering violations, missing UI states, numbers computed in the
UI, edited migrations, secrets in code or logs, missing tests for new behaviour, outdated docs.
