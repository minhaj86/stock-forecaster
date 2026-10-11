---
name: backend-feature
description: Step-by-step guide for adding or changing backend behaviour in the StockLens Spring Boot modular monolith (domain model, Flyway migration, repository port, use case, REST endpoint, errors, events, caching, tests). Use when implementing or modifying Java code under backend/.
---

# Backend feature (Spring Boot 4 modular monolith)

Read `backend/AGENTS.md` first. Use existing code as templates: `watchlist` is the most complete CRUD module,
`marketdata` shows JDBC, jobs and provider ports, `quant` and `forecasting` show pure domain math (and
`forecasting` a provider port with configurable models), `analytics` and `forecasting` show data-versioned caching.

## 1. Choose the module and the published API

- Put the behaviour in the module that owns the data (see the module table in `backend/AGENTS.md`). A new bounded
  context becomes a new top-level package with a `package-info.java`:
  ```java
  @ApplicationModule(displayName = "Notifications")
  package com.stockforecast.notification;
  import org.springframework.modulith.ApplicationModule;
  ```
- If another module needs it, expose a small interface, record or event in the module's **top-level package**.
  Keep the dependency graph acyclic.

## 2. Schema (if needed)

Add `backend/src/main/resources/db/migration/V<next>__<module>_<change>.sql` (rules:
`.github/instructions/flyway-migrations.instructions.md`). Never edit an existing migration.

## 3. Domain (`<module>/domain`)

- Aggregate with a private constructor, a static factory and intention-revealing methods that take `Instant now`
  and enforce invariants (throw a `DomainException` subclass with a stable code).
- `@Id private UUID id`, assigned from `Identifiers.newId()` in the constructor (UUIDv7); `@Version private Long version`
  left `null` until persisted (tells Spring Data the entity is new); `equals`/`hashCode` on id.
- Repository **port** as a plain interface with Spring Data-compatible method names, scoped by owner where relevant:
  ```java
  public interface ThingRepository {
      Thing save(Thing thing);
      Optional<Thing> findByIdAndUserId(UUID id, UUID userId);
  }
  ```
- Exceptions: one class per failure (or a grouping class like `WatchlistExceptions`), e.g.
  `super("THING_NOT_FOUND", ErrorCategory.NOT_FOUND, "Thing not found")`. Messages must be client-safe.

## 4. Persistence (`<module>/infrastructure/persistence`)

```java
interface SpringDataThingRepository extends Repository<Thing, UUID>, ThingRepository {
    @Override
    @EntityGraph(attributePaths = "items")   // add @Query/@Lock/@EntityGraph by overriding port methods
    Optional<Thing> findByIdAndUserId(UUID id, UUID userId);
}
```

High-volume time series: `NamedParameterJdbcTemplate` with natural keys and `INSERT … ON CONFLICT … DO UPDATE`
(see `JdbcMarketDataStore`).

## 5. Use case (`<module>/application`)

- `@Service` with constructor injection; `@Transactional` on writes and `@Transactional(readOnly = true)` on reads;
  inject `Clock`.
- Every user-scoped method takes the authenticated user's id and loads with it (`findByIdAndUserId`), so a foreign
  id behaves like a missing one (404).
- Return read-model records (views), never entities, to the API layer.
- Cross-module invariant in the same transaction → publish an event and handle it with a synchronous
  `@EventListener`. Slow or optional reaction → `@Async @TransactionalEventListener` (public method).
- External systems → a port interface in `application` with an adapter in `infrastructure`.

## 6. REST API (`<module>/api`)

```java
@Tag(name = "Things")
@RestController
@RequestMapping("/api/v1/things")
class ThingController {
    @Operation(summary = "Create a thing")
    @PostMapping
    ResponseEntity<ThingResponse> create(@CurrentUser AuthenticatedUser user, @Valid @RequestBody CreateThingRequest request) {
        ThingResponse created = ThingResponse.from(service.create(user.id(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/things/" + created.id())).body(created);
    }
}
```

- Request/response records live in `api` with Bean Validation; no class-level `@Validated`.
- New **public** endpoints must be added to `identity/infrastructure/security/SecurityConfiguration`; everything
  else requires authentication by default. Admin endpoints: `/api/v1/admin/**` plus `@PreAuthorize("hasRole('ADMIN')")`.
- Enum-like query parameters: bind to a type with a `Converter` (see `shared/web/MarketPeriodConverter`), so invalid
  values become 400 `INVALID_PARAMETER`.

## 7. Caching (optional)

Cache only expensive reads. Declare caches next to the code (`RedisCacheManagerBuilderCustomizer` +
`CacheConfiguration.typedJsonCache(defaults, jsonMapper, javaType, ttl)`); include a data version in keys instead of
evicting. If the shape of a cached type changes, bump `spring.cache.redis.key-prefix` (`sf:v1:` → `sf:v2:`) in
`application.yml` so stale JSON is never read.

## 8. Tests

- Unit (`*Test`): domain invariants, use-case branches with Mockito, any math with independent golden values.
- Integration (`*IT`, `@IntegrationTest`): drive the HTTP API with `AuthTestClient`/`WatchlistTestClient`; assert
  status, JSON and error `code`; add **ownership tests** (user B gets 404 for user A's resource and cannot mutate it);
  validation failures; async effects with Awaitility.
- Persistence-specific behaviour (constraints, locks): `@DataJpaTest` + `PostgresTestcontainersConfiguration`.
- Run `./mvnw test` while iterating and `./mvnw verify` before finishing. Architecture tests must stay green.

## 9. Configuration and docs

New settings: `@ConfigurationProperties` record with `@Validated`/`@DefaultValue`, env var in `application.yml`,
documented in `.env.example` and the README configuration table. Update `docs/architecture.md` (module and API
tables).
