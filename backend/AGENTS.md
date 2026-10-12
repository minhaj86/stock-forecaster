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
marketdata  provider port + adapters, job queue/worker, stored   -> stock, watchlist
            bars, sync status, provider health/usage
analytics   metrics, performance, dashboard                     -> marketdata, stock, watchlist, identity
admin       back office: /api/v1/admin/**, dashboard, audit log  -> identity, user, watchlist, stock, marketdata
```

- The graph must stay **acyclic**. Do not import another module's `domain`/`application`/`infrastructure`/`api`
  types. If you need something, add it to that module's top-level package (record, interface or event).
- Published APIs today: `identity.{AuthenticatedUser, CurrentUser, Role, AccountStatus, UserRegisteredEvent,
  UserAccountDeleted, AccountAdministration, ManagedAccount, AccountStatistics}`, `user.UserProfiles`,
  `stock.{StockCatalog, StockRef, AssetType, UnknownStockException, StockAdministration, ManagedStock}`,
  `watchlist.{WatchlistQueries, StockAddedToWatchlist}`,
  `marketdata.{PriceHistory, DailyBar, MarketDataStatus, MarketDataUpdated, MarketDataJobs, MarketDataJobView,
  MarketDataInventory, MarketDataProviders, MarketDataInterval, MarketDataJob{Type,Status,Trigger},
  ExistingDataStrategy, ProviderCapabilities, ProviderHealth, ProviderInstrumentMatch, ProviderStockMetadata}`.
- The `admin` module only orchestrates these published administration APIs and audits every change; business rules
  stay in the owning module (e.g. the last-administrator rule in identity, safe re-population in marketdata). New
  operational functions: add the rule/use case to the owning module's published API, then the endpoint, audit entry
  and UI in `admin`.
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
| Reaction / slow work | `@Async @TransactionalEventListener` (after commit); **method must be `public`** for proxies | `analytics/application/AnalyticsRecalculation.java` |
| Scheduled job | `@Scheduled(cron = "${…}", zone = "UTC") @SchedulerLock(name = …)` (public method); cron `-` disables it | `marketdata/infrastructure/scheduling/ScheduledMarketDataSync.java` |
| Background job queue | Entity with a state machine (`marketdata/domain/MarketDataJob`), queued in the trigger's transaction, claimed with `FOR UPDATE SKIP LOCKED`, executed by a ShedLock-serialised worker that is woken up after commit; outside calls happen without a transaction; transient failures re-queue with backoff | `marketdata/application/MarketDataSynchronizationService.java` |
| Admin operation | Endpoint in `admin/api`, use case in `admin/application` (`@PreAuthorize("hasRole('ADMIN')")`, transaction) calling the owning module's published API and `AuditLog.record(...)` in the same transaction | `admin/application/AdminUserService.java` |
| Cache | Declare per module with a `RedisCacheManagerBuilderCustomizer` using `CacheConfiguration.typedJsonCache(...)` (typed JSON, TTL). Put a **data version** in keys instead of evicting. Cache failures are logged, never fatal. When a cached type's shape changes, bump `spring.cache.redis.key-prefix` (`sf:v1:` → `sf:v2:`) | `analytics/infrastructure/AnalyticsCacheConfiguration.java` |
| Configuration | `@ConfigurationProperties` **records** with `@Validated` + `@DefaultValue`, owned by the layer that uses them; env vars mapped in `application.yml`; fail fast on invalid values | `marketdata/application/MarketDataProperties.java` |
| Pagination | `shared.api.PageResponse` (`{content, page:{number,size,totalElements,totalPages}}`) | `stock/api/StockController.java` |

## Security

- JWT access tokens are minted and validated by Spring Security's Nimbus support (`identity/infrastructure/security`).
  There is **no custom JWT filter**; keep it that way. Tokens carry `typ=at+jwt`, `iss`, `aud`, `sub` = user id,
  `email` and `roles`.
- `SecurityConfiguration` is deny-by-default. New public endpoints must be added there deliberately, and
  `/api/v1/admin/**` requires an `ADMIN` token **and** a currently active administrator account
  (`AccessPolicies.activeAdministrator`), plus `@PreAuthorize("hasRole('ADMIN')")` on every admin service.
- Tokens of users with a pending forced password change carry the `PASSWORD_CHANGE_REQUIRED` authority and may only
  call `GET /users/me` and `/auth/*` (403 `PASSWORD_CHANGE_REQUIRED` otherwise).
- Admin actions are audited with `admin.application.AuditLog` (metadata keys naming credentials are dropped, but never
  pass secrets in the first place).
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

- Never call a `MarketDataProvider` from a request path. Ingestion always runs as jobs (`MarketDataJobService`
  queues, `MarketDataSynchronizationService` executes): watching a stock without data, the daily ShedLock job,
  start-up catch-up and admin requests. Only the admin provider search/metadata lookup and health check call the
  provider synchronously — never inside a DB transaction (`ProviderUsageRecorder` writes in `REQUIRES_NEW`).
- The `synthetic` provider must keep reporting `synthetic: true`. Real adapters live in
  `marketdata/infrastructure/provider/<vendor>/` behind `@ConditionalOnMarketDataProvider(MarketDataProviderType.X)`
  (accepts `twelve-data` and `TWELVE_DATA`), acquire `ProviderThrottle` permits per vendor request, record usage and
  throw `MarketDataProviderException` with a `ProviderErrorCode` (see the `market-data-provider` skill).
- Prices: OHLC split-adjusted, `adjusted_close` split- and dividend-adjusted; validate every response with
  `MarketDataValidationService` before writing; destructive replacement only through `MarketDataRepopulationService`
  (stage, verify, swap in one transaction).
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
  `support/AuthTestClient` (register/login), `support/WatchlistTestClient` (watchlists, `awaitMarketData`) and
  `support/AdminTestClient` (`newAdmin()`, `registerStock`, `backfill`, `awaitJob`; `uniqueSymbol()` for isolated
  synthetic stocks). Programmatic transactions in unit tests: `support/NoOpTransactions.template()`.
- A test that needs a database it fully controls (e.g. "the last administrator") or another provider runs in its own
  context: `@TestPropertySource` with distinct properties (see `AdminSafeguardsIT`, `TwelveDataIntegrationIT` with
  WireMock via `@DynamicPropertySource`). Use this sparingly: every extra context starts its own containers.
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
- `@Modifying(clearAutomatically = true)` detaches loaded entities; only read their in-memory state afterwards.
- Bucket4j's blocking `tryConsume` rejects a zero wait; `Bucket4jProviderThrottle` handles that.
- WireMock 3 standalone (shaded) is used for HTTP stubs; it does not clash with Jackson 3 or Tomcat.

## Commands

```bash
./mvnw test                                    # unit + architecture (seconds, no Docker)
./mvnw verify                                  # + integration tests (needs Docker/Podman, ~3 min)
./mvnw test -Dtest=WatchlistTest               # one unit test class
./mvnw verify -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=WatchlistApiIT   # one IT
./mvnw spring-boot:run -Dspring-boot.run.profiles=local                                             # API on :8080
```
