---
name: market-data-provider
description: How to add a licensed market-data provider adapter (e.g. Twelve Data, Alpha Vantage, Polygon) behind the MarketDataProvider port in the StockLens backend, including configuration, error mapping, rate limiting, resilience and WireMock tests without real API calls. Use when integrating or changing a market-data vendor.
---

# Adding a market-data provider adapter

The rest of the application must not know which vendor is used. Everything vendor-specific (DTOs, URLs, error
payloads, symbol formats) stays in
`backend/src/main/java/com/stockforecast/marketdata/infrastructure/provider/<vendor>/` and is normalised into the
port's types immediately. The reference implementation is `provider/twelvedata/` (client, DTOs, adapter,
configuration) with `TwelveDataMarketDataProviderTest` (WireMock) and `TwelveDataIntegrationIT`.

## Port to implement

`marketdata/application/MarketDataProvider.java`:

- `ProviderInfo info()`: id (kebab-case, e.g. `twelve-data`), display name, `synthetic = false`, description.
- `ProviderCapabilities capabilities()`: supported intervals, earliest date, max bars per request, `supportsSearch`,
  `adjustedPrices`. Admin requests for unsupported intervals are rejected with these.
- `boolean isConfigured()` (e.g. API key present) and `requiresApiKey()`; the admin UI only shows
  "configured/missing", never the key.
- `List<ProviderInstrumentMatch> searchStocks(String query)` and
  `Optional<ProviderStockMetadata> getStockMetadata(ProviderInstrument)`: normalised name, exchange, MIC, ISO
  currency, country, asset type — used by stock registration in the admin console.
- `HistoricalPrices getHistoricalPrices(ProviderInstrument, LocalDate from, LocalDate to, MarketDataInterval)`:
  oldest first, inclusive, **completed sessions only**; open/high/low/close split-adjusted, `adjustedClose` split-
  **and** dividend-adjusted; `BigDecimal` without extra rounding. Split long ranges into several vendor requests.
  Return what the vendor says the data is about (`symbol`, `currency`); `MarketDataValidationService` checks them
  and every bar before anything is stored, so do not "repair" data silently.
- `Optional<LatestQuote> getLatestQuote(ProviderInstrument)` and `ProviderHealth checkHealth()` (one cheap request;
  `HEALTHY`, `DEGRADED`, `UNAVAILABLE` or `MISCONFIGURED`).
- Failures: throw `MarketDataProviderException(ProviderErrorCode, message[, retryAfter, cause])`. The code decides
  the job's behaviour: `TIMEOUT`, `RATE_LIMITED` (pass `Retry-After`), `UNAVAILABLE` are retried with backoff;
  `AUTHENTICATION_FAILED`, `PLAN_RESTRICTED`, `NOT_CONFIGURED`, `SYMBOL_NOT_FOUND`, `INVALID_REQUEST`,
  `INVALID_RESPONSE` are not. Messages are shown to administrators: client-safe, no URLs with keys, no stack traces.

## Steps

1. **Dependencies** (`backend/pom.xml`): `spring-boot-starter-restclient` and `bucket4j_jdk17-core` are already
   there; WireMock standalone is the test stub server.
2. **Configuration**: a `@Validated @ConfigurationProperties("app.market-data.<vendor>")` record (`baseUrl`,
   `apiKey`, timeouts, `requestsPerMinute`, `maxWait`, max bars per request); env vars in `application.yml`,
   `.env.example`, `docker-compose.yml` and the README. Never log the key (send it in a header if the vendor allows).
3. **Selection**: add a constant to `MarketDataProviderType` (accepts `VENDOR_NAME` and `vendor-name`) and annotate
   the `@Configuration` with `@ConditionalOnMarketDataProvider(MarketDataProviderType.X)`.
4. **Adapter**: build a dedicated `RestClient` with connect/read timeouts. Package-private vendor DTOs; map HTTP
   status *and* error bodies (some vendors answer errors with HTTP 200) to `ProviderErrorCode`; empty ranges are not
   errors.
5. **Rate limits**: create a `Bucket4jProviderThrottle` sized from the plan and call `acquire(permits, maxWait)`
   before **every** vendor request; on 429 call `pauseFor(retryAfter)` so all callers back off. Record each request
   with `ProviderUsageRecorder` (success/failure; it writes in its own transaction) and count
   `market_data_provider_requests` / `market_data_provider_errors` / `market_data_provider_rate_limit` metrics.
6. **Tests** (no real network): WireMock stubs for 200, empty range, 401, 404/unknown symbol, 429 with
   `Retry-After`, 5xx, malformed JSON and timeouts. Assert mapping (dates, both price series, decimals, unfinished
   sessions dropped), chunking, throttling and error classification. Add an integration test in its own context
   (`@TestPropertySource` + `@DynamicPropertySource` with the WireMock URL) that registers a stock through the admin
   API and checks the stored bars.
7. **Operations**: `prod` should configure a real provider; the `synthetic` provider stays for local demos and
   tests. Update `docs/architecture.md` §7 and the README provider section.

## Invariants to keep

- No provider calls in user request paths. Price ingestion only runs as `MarketDataJob`s through
  `MarketDataJobService`/`MarketDataSynchronizationService` (watch-triggered initial loads, the daily scheduled sync,
  start-up catch-up, admin jobs). Only the admin search/metadata lookup and health check call the provider directly,
  and never inside a database transaction.
- Only active, watched stocks are synchronised automatically; administrators can load any active stock.
- Writes are idempotent on the daily table's key `(stock_id, bar_date)` (an intraday interval would get its own
  table); incremental updates re-fetch a short correction window (`app.market-data.refresh-overlap-days`). Destructive replacement only through `MarketDataRepopulationService` (stage, verify,
  swap in one transaction) so a provider failure never deletes stored data.
- One adjustment convention for every provider (see the port Javadoc); analytics depend on it.
