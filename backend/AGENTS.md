# backend/AGENTS.md — Spring Boot modular monolith

Read the root [`AGENTS.md`](../AGENTS.md) first (golden rules, API contract, Definition of Done). This file adds
backend specifics. Design rationale lives in [`docs/architecture.md`](../docs/architecture.md).

## Stack

Java 25 · Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7, **Jackson 3**) · Spring Modulith 2.1 ·
PostgreSQL 18 · Flyway · Redis 8 (Spring Cache) · ShedLock 7 · springdoc 3 · JUnit 6 + AssertJ + Mockito ·
Testcontainers 2. Virtual threads are on (`spring.threads.virtual.enabled`).

## Modules and allowed dependencies

Every direct sub-package of `com.stockforecast` is a module. **Its top-level package is its published API**; the
`domain`, `application`, `infrastructure` and `api` sub-packages are internal to it.

```text
shared      (open kernel: errors, ids, MarketPeriod, web plumbing, cache helper) <- everyone
identity    accounts, sessions, JWT, security config            -> shared
user        profiles                                            -> identity
stock       catalogue, search                                   -> shared
watchlist   watchlists, items, limits, starter list             -> identity, stock
marketdata  provider port, ingestion, stored bars, sync status  -> stock, watchlist
analytics   metrics, performance, dashboard                     -> marketdata, stock, watchlist, identity
```

- The graph must stay **acyclic**. Do not import another module's `domain`/`application`/`infrastructure`/`api`
  types. If you need something, add it to that module's top-level package (record, interface or event).
- Published APIs today: `identity.{AuthenticatedUser, CurrentUser, Role, UserRegisteredEvent}`,
  `stock.{StockCatalog, StockRef, AssetType, UnknownStockException}`,
  `watchlist.{WatchlistQueries, StockAddedToWatchlist}`,
  `marketdata.{PriceHistory, DailyBar, MarketDataStatus}`.
- Reference other modules' data **by id** (e.g. `stock_id`), never with a JPA association across modules.
- Verify with `./mvnw test -Dtest=ModularityTest,LayeredArchitectureTest`. Module diagrams are written to
  `target/spring-modulith-docs/`.

## Layering inside a module

`api → application → domain`; `infrastructure → application/domain`. Enforced rules (see
`src/test/java/com/stockforecast/architecture/LayeredArchitectureTest.java`):

- `domain` must not depend on Spring, `jakarta.servlet`, Jackson, or outer layers. JPA annotations on entities are
  the one accepted exception (documented trade-off).
- `application` must not depend on `api` or `infrastructure`.
- `api` must not touch `@Entity` types or anything named `*Repository`.
- No field injection, no `java.util.logging`, no `System.out`, no throwing generic `Exception`/`RuntimeException`.

## Patterns to copy

| Concern | How | Example |
|---|---|---|
| Aggregate / entity | Factory method + intention-revealing mutators taking `Instant now`; `id` from `Identifiers.newId()` (UUIDv7); **nullable `@Version Long version`** (otherwise Spring Data's `save()` does merge + SELECT for assigned ids); `equals`/`hashCode` on id; `toString` without secrets | `watchlist/domain/Watchlist.java` |
| Repository port | Interface in `domain` with Spring Data-compatible method names; an infrastructure interface `extends Repository<E, ID>, ThePort` implements it (no adapter class). Override methods there to add `@Query`/`@Lock`/`@EntityGraph` | `watchlist/infrastructure/persistence/SpringDataWatchlistRepository.java` |
| Time series | Plain JDBC (`NamedParameterJdbcTemplate`), natural keys, batched `INSERT … ON CONFLICT DO UPDATE` | `marketdata/infrastructure/persistence/JdbcMarketDataStore.java` |
| Use case | `@Service` in `application`, explicit `@Transactional` (readOnly for queries), inject `Clock` (never `Instant.now()`), take the caller's user id and scope every lookup by it | `watchlist/application/WatchlistService.java` |
| Controller | `@RestController` in `api`; `@CurrentUser AuthenticatedUser user`; request/response **records** in `api` with Bean Validation; `@Operation`/`@Tag`; `201` + `Location` for creates, `204` for deletes. **No class-level `@Validated`** (Spring 7 validates `@RequestParam` constraints itself) | `watchlist/api/WatchlistController.java` |
| Business error | Subclass `shared.domain.DomainException(code, ErrorCategory, clientSafeMessage)`; `GlobalExceptionHandler` maps the category to HTTP. Messages must be safe to show to users | `watchlist/domain/WatchlistExceptions.java` |
| Invariant across modules | Synchronous `@EventListener` (same transaction), e.g. profile, starter watchlist, start tracking | `marketdata/application/StockTrackingStarter.java` |
| Reaction / slow work | `@Async @TransactionalEventListener` (after commit); **method must be `public`** for proxies | `marketdata/application/WatchedStockBackfill.java` |
| Scheduled job | `@Scheduled(cron = "${…}", zone = "UTC") @SchedulerLock(name = …)` (public method); cron `-` disables it | `marketdata/infrastructure/scheduling/MarketDataSyncJob.java` |
| Cache | Declare per module with a `RedisCacheManagerBuilderCustomizer` using `CacheConfiguration.typedJsonCache(...)` (typed JSON, TTL). Put a **data version** in keys instead of evicting. Cache failures are logged, never fatal. When a cached type's shape changes, bump `spring.cache.redis.key-prefix` (`sf:v1:` → `sf:v2:`) | `analytics/infrastructure/AnalyticsCacheConfiguration.java` |
| Configuration | `@ConfigurationProperties` **records** with `@Validated` + `@DefaultValue`, owned by the layer that uses them; env vars mapped in `application.yml`; fail fast on invalid values | `marketdata/application/MarketDataProperties.java` |
| Pagination | `shared.api.PageResponse` (`{content, page:{number,size,totalElements,totalPages}}`) | `stock/api/StockController.java` |

## Security

- JWT access tokens are minted and validated by Spring Security's Nimbus support (`identity/infrastructure/security`).
  There is **no custom JWT filter**; keep it that way. Tokens carry `typ=at+jwt`, `iss`, `aud`, `sub` = user id,
  `email` and `roles`.
- `SecurityConfiguration` is deny-by-default. New public endpoints must be added there deliberately, and
  `/api/v1/admin/**` requires `ADMIN` (plus `@PreAuthorize` on the service).
- Refresh tokens: opaque, SHA-256 hashed at rest, rotated on every use, replay revokes the family; persisted
  "remember me" flag. Passwords: BCrypt via `DelegatingPasswordEncoder`, 12 characters to 72 UTF-8 bytes.
- Foreign or missing user-scoped resources → the module's `*_NOT_FOUND` (404). Never 403 for ownership checks.

## Database and migrations

- Flyway owns the schema (`src/main/resources/db/migration`), Hibernate only validates (`ddl-auto: validate`).
  Name: `V<next>__<module>_<change>.sql`. Never modify an applied migration.
- Types: `UUID` ids, `TIMESTAMPTZ` (UTC) instants, `NUMERIC(19,6)` prices, `VARCHAR` + `CHECK` constraints instead
  of `CHAR(n)` (Hibernate validation rejects `bpchar` for `String`), explicit indexes for every query path.
- Each table belongs to one module. A cross-module FK is acceptable for integrity; document it in the migration.
- Do not enable `hibernate.order_inserts`: cross-aggregate FKs are plain id columns that Hibernate can't see.

## Market data and analytics

- Never call a `MarketDataProvider` from a request path. Ingestion runs on watch (async backfill), on the daily
  ShedLock job and on start-up catch-up; claims use a DB lease so no connection is held during provider calls.
- The `synthetic` provider must keep reporting `synthetic: true`. Real adapters live in
  `marketdata/infrastructure/provider/<vendor>/` behind `@ConditionalOnProperty(app.market-data.provider=<id>)`
  (see the `market-data-provider` skill).
- Metric definitions live in the pure `quant` module (`quant/PriceSeries.java`, trailing series in
  `quant/Indicators.java`), shared by analytics and forecasting so neither depends on the other. Return
  `OptionalDouble`/empty (or NaN warm-up values) when history is too short. New metrics need **golden-value tests
  computed independently** (see the `analytics-metric` skill). Ratios are decimal fractions, rounded only at the API
  edge (6 decimals; RSI 2).

## Forecasting

- Models implement `forecasting/domain/StockForecastModel` and are plain classes wired as beans in
  `forecasting/infrastructure/ForecastingConfiguration`; extend `CalibratedPricePathModel` to get uncertainty ranges
  calibrated on the model's own out-of-sample errors. See the `forecast-model` skill.
- **No look-ahead, ever**: a model may only use the `ForecastInput` it receives. The backtester passes
  `input.truncate(origin + 1)`; features must be trailing (no whole-dataset normalisation). Every model must pass
  `WalkForwardBacktesterTest.forecastsAreUnaffectedByLaterObservations`.
- Numerics come from Hipparchus 4 (`SimpleRegression`, `NormalDistribution`, `BrentOptimizer`, `BOBYQAOptimizer`);
  do not hand-roll algorithms a maintained library provides. Golden values come from numpy/scipy/statsmodels.
- Planned models (`MONTE_CARLO`, `ARIMA`) stay unavailable until really implemented; requests get
  `UNSUPPORTED_FORECAST_MODEL`. Too little history is a 422 `INSUFFICIENT_FORECAST_HISTORY` with `details`, never a
  weaker forecast.
- Results are cached under keys containing the settings fingerprint and the data version; changing a cached record's
  shape requires bumping `spring.cache.redis.key-prefix`.

## Testing

- `*Test` = fast unit/architecture tests (Surefire, `./mvnw test`); `*IT` = container-backed (Failsafe,
  `./mvnw verify`).
- Integration tests use the `@IntegrationTest` meta-annotation (`support/IntegrationTest.java`): full context,
  MockMvc with the real filter chain, PostgreSQL and Redis via Testcontainers. Drive the API with
  `support/AuthTestClient` (register/login) and `support/WatchlistTestClient` (watchlists, `awaitMarketData`).
- Tests share one context and database: use unique users (`AuthTestClient.uniqueEmail()`) and never assume global
  counts. Wait for async work with Awaitility, never `Thread.sleep`.
- Persistence tests: `@DataJpaTest @AutoConfigureTestDatabase(replace = NONE) @Import(PostgresTestcontainersConfiguration.class)`.
- Mockito runs as a java agent (configured in `pom.xml`). Boot 4 removed `@MockBean`: use Spring's
  `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).

## Spring Boot 4 and library gotchas

- Modular starters: `spring-boot-starter-webmvc`, `-flyway`, `-security-oauth2-resource-server`; test slices live in
  new packages: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`,
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
  `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`.
- Jackson 3: inject `tools.jackson.databind.json.JsonMapper`; annotations remain `com.fasterxml.jackson.annotation.*`.
- Testcontainers 2: `org.testcontainers.postgresql.PostgreSQLContainer`; Redis via `GenericContainer` with
  `@ServiceConnection(name = "redis")`.
- `spring.datasource.hikari.connection-timeout` is a raw **millisecond** number, not a `Duration`.
- Spring Security 7 validates the JWT `typ` header by default and publishes RFC 9728 metadata at
  `/.well-known/oauth-protected-resource` (customised in `SecurityConfiguration`).
- Micrometer gauges must reference a mutable holder (`AtomicLong`) registered once.
- `@Async`, `@SchedulerLock` and `@Transactional` only work through proxies: public methods, no self-invocation.

## Commands

```bash
./mvnw test                                    # unit + architecture (seconds, no Docker)
./mvnw verify                                  # + integration tests (needs Docker/Podman, ~3 min)
./mvnw test -Dtest=WatchlistTest               # one unit test class
./mvnw verify -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=WatchlistApiIT   # one IT
./mvnw spring-boot:run -Dspring-boot.run.profiles=local                                             # API on :8080
```
