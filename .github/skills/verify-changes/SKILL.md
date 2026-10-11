---
name: verify-changes
description: Runs and interprets the StockLens quality gates (backend unit, architecture and Testcontainers integration tests; frontend lint, type-check, Vitest and production build), including JDK 25 and Docker/Podman setup and common failure diagnosis. Use before finishing any change, when asked to run tests or a build, or when tests fail.
---

# Verify changes

## Run everything

```bash
.github/skills/verify-changes/scripts/verify.sh            # full: backend verify + frontend checks (~4–5 min)
.github/skills/verify-changes/scripts/verify.sh --quick    # skip container-backed integration tests (~30 s)
.github/skills/verify-changes/scripts/verify.sh --backend-only | --frontend-only
```

The script (in this skill's `scripts/` directory) locates JDK 25 (macOS `java_home`), detects Docker or a Podman
machine (setting `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`), installs frontend dependencies if
missing, and stops at the first failure. Run the quick mode while iterating and the full mode before finishing.

## Individual commands

| Scope | Command (from `backend/` or `frontend/`) |
|---|---|
| Backend unit + architecture | `./mvnw test` |
| Backend everything | `./mvnw verify` |
| One unit test class / method | `./mvnw test -Dtest=WatchlistTest` · `-Dtest='WatchlistTest#isLimitedToFiftyStocks'` |
| One integration test | `./mvnw verify -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=WatchlistApiIT` |
| Module boundaries only | `./mvnw test -Dtest='ModularityTest,LayeredArchitectureTest'` |
| Frontend all | `npm run lint && npm run typecheck && npm test && npm run build` |
| One frontend file | `npx vitest run src/features/watchlist/WatchlistDetailPage.test.tsx` |

Reports: `backend/target/surefire-reports`, `backend/target/failsafe-reports`; module diagrams in
`backend/target/spring-modulith-docs`.

## Diagnosing failures

| Symptom | Cause and fix |
|---|---|
| `requireJavaVersion` enforcer failure / class file version errors | Wrong JDK. `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` (macOS) or point `JAVA_HOME` at JDK 25. |
| `Could not find a valid Docker environment` | Start Docker or `podman machine start`; for Podman export `DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. |
| `Violations detected` from `ModularityTest` | A module referenced another module's internal package or created a cycle. Move the type to the target module's top-level package or invert the dependency with an event. |
| `LayeredArchitectureTest` failure | Wrong layer dependency (e.g. `domain` importing Spring, controller using a repository or entity). Move the code to the right layer. |
| `Schema-validation: wrong column type` at startup | Entity mapping and migration disagree (e.g. `CHAR` vs `VARCHAR`, missing column). Add a new migration; never edit an applied one. |
| `Failed to bind properties under …` | A configuration value has the wrong type (e.g. Hikari timeouts are milliseconds, not `5s`). |
| Integration test sees data from other tests | Tests share one database: use unique users (`AuthTestClient.uniqueEmail()`) and never assert global counts. |
| Frontend test: `Unable to find role … and name …` | The element is missing an accessible name (fix the component, e.g. MUI 9 `slotProps.htmlInput`), it renders asynchronously (use `findBy*`), or it appears twice (scope with `within(region)`). |
| Frontend test fails with an unhandled request | Add an MSW handler in `src/test/handlers.ts` or `server.use(...)` in the test; specific paths go before `/:param` paths. |
| `tsc` errors after dependency updates | Check the major-version notes in `frontend/AGENTS.md` (MUI 9 icon names and `slotProps`, Zod 4 `{ error }`, React Router 8 imports). |

Never weaken an assertion, delete a test or edit a golden value just to make a build pass. Find the cause first.
