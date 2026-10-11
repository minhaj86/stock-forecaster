---
name: full-stack-feature
description: End-to-end workflow and Definition of Done for building a product feature in StockLens (Spring Boot API, React UI, tests on both sides, docs). Use when asked to implement, add or extend a user-facing feature, endpoint or screen, or when a change spans backend and frontend.
---

# Full-stack feature workflow

A feature is **not** done because an endpoint exists or a screen renders. It is done when the backend capability,
the UI connected to it, both test suites and the docs are complete. Before writing code, answer:

1. What backend capability is required, and in which module does it belong?
2. What frontend experience is required (including loading, empty, error and pending states)?
3. What API contract connects them (method, path, request/response, status codes, error codes)?
4. Which tests cover both sides (including ownership and failure paths)?

## Step 1 — Inspect before inventing

- Search the controllers for existing endpoints: `grep -rn "@RequestMapping\|@GetMapping\|@PostMapping\|@PatchMapping\|@DeleteMapping" backend/src/main/java`.
  With the backend running, the live contract is at `http://localhost:8080/v3/api-docs`.
- Check `frontend/src/api/*Api.ts` and `frontend/src/models/*` for existing client functions and types.
- Read the design for the area in `docs/architecture.md` (module table §2, API table §6) and follow it unless there
  is a good reason not to; then update the doc.
- **Never invent a frontend contract for an endpoint that exists**, and never invent backend behaviour the UI does
  not need.

## Step 2 — Design the contract

Write down (in the PR description or `docs/architecture.md` §6):

- `METHOD /api/v1/...`, request record and validation rules, response record (field names exactly as JSON).
- Status codes: `200`/`201 + Location`/`204`; errors via the `ApiError` envelope with stable `code`s
  (`*_NOT_FOUND`, `VALIDATION_FAILED`, `*_LIMIT_REACHED` …).
- Ownership: user-scoped resources come from the principal; foreign ids → 404.
- Units: ratios as decimal fractions, ISO instants, `yyyy-MM-dd` dates, `null` for unavailable metrics.

## Step 3 — Backend

Follow the `backend-feature` skill. Minimum: domain rules, use case, controller with OpenAPI annotations, migration
if needed, unit tests, `*IT` integration tests including validation and **user A vs user B** ownership.

## Step 4 — Frontend

Follow the `frontend-feature` skill. Minimum: types in `models/`, API function, query keys, hooks with correct
invalidation, UI with skeleton/empty/error (and pending for market data), accessible controls, MSW-backed tests for
the happy path and at least one error or empty path.

## Step 5 — Verify

Run `.github/skills/verify-changes/scripts/verify.sh` (or the commands in the `verify-changes` skill). All of
backend `./mvnw verify` and frontend lint, type-check, tests and build must pass. For user-visible changes, also
exercise the running stack (`local-stack` skill).

## Step 6 — Document

- `README.md`: API overview table, configuration table, feature list, known limitations.
- `docs/architecture.md`: mark implemented items ✅ and record new trade-offs.
- Agent files (`AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`) when conventions or commands change.

## Definition of Done

- [ ] Backend implemented in the owning module; boundaries and layering tests green
- [ ] Backend unit tests + integration tests (happy path, validation, errors, ownership)
- [ ] OpenAPI annotations; error codes in `DomainException` subclasses
- [ ] Frontend connected to the real endpoint (no mock contract)
- [ ] Loading, empty, error (+ retry) and pending states where relevant
- [ ] Frontend tests (MSW) pass; lint and type-check clean
- [ ] Both builds succeed; docs updated

## Worked example: editing watchlist notes

The backend already supports notes (`PATCH /api/v1/watchlists/{id}/items/{symbol}` with `{"notes": "…"}`, see
`watchlist/api/WatchlistController.java`), but the UI cannot edit them. So:

1. Backend: no change needed. Confirm the endpoint is covered by `WatchlistApiIT` (it is: `addsListsAndRemovesStocks`).
2. Frontend: add `changeNotes(id, symbol, notes)` to `api/watchlistApi.ts`; add `useChangeNotes()` in
   `features/watchlist/hooks.ts` invalidating `queryKeys.watchlist(id)`; add a notes column or dialog to
   `WatchlistDetailPage` with `WatchlistItem.notes` (≤ 500 characters, mirrored in a Zod schema); show save errors
   through `useNotify()`.
3. Tests: an MSW handler capturing the PATCH body; assert the success notification and the updated value; one error
   case (e.g. 400 `VALIDATION_FAILED` for notes over 500 characters).
4. Docs: README frontend feature list.
