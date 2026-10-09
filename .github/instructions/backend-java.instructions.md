---
applyTo: "backend/src/**/*.java"
---

# Backend Java rules (details: backend/AGENTS.md)

- Place code in the owning module and layer: `domain` (entities, value objects, ports, domain exceptions),
  `application` (use cases, `@Transactional`, ports to external systems), `infrastructure` (Spring Data/JDBC
  adapters, provider adapters, configuration, jobs), `api` (controllers and request/response records).
- Cross-module access only through the other module's top-level package. Keep the module graph acyclic.
- Constructor injection only. Inject `Clock`; never call `Instant.now()`/`LocalDate.now()` without a clock.
- Entities: factory methods and intention-revealing mutators, ids from `Identifiers.newId()`, nullable
  `@Version Long`, `equals`/`hashCode` on id, no secrets in `toString()`.
- Controllers: `@CurrentUser AuthenticatedUser`, Bean Validation on request records, OpenAPI `@Operation`, no
  class-level `@Validated`, no repositories or entities.
- Errors: `DomainException` subclasses with stable codes and client-safe messages. No `catch (Exception)`, no empty
  catch blocks, no generic `RuntimeException`.
- `@Async`, `@Scheduled` + `@SchedulerLock`, `@Transactional` and event listeners need public methods and must not
  be self-invoked.
- Never call an external provider inside a user request. Market data is read from the database.
- Tests: `*Test` for unit tests (Mockito/AssertJ), `*IT` for `@IntegrationTest` (Testcontainers). Cover validation,
  error codes and ownership. Use Awaitility for async work, never sleeps.
