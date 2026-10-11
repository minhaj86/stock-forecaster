---
name: market-data-provider
description: How to add a licensed market-data provider adapter (e.g. Twelve Data, Alpha Vantage, Polygon) behind the MarketDataProvider port in the StockLens backend, including configuration, error mapping, rate limiting, resilience and WireMock tests without real API calls. Use when integrating or changing a market-data vendor.
---

# Adding a market-data provider adapter

The rest of the application must not know which vendor is used. Everything vendor-specific stays in
`backend/src/main/java/com/stockforecast/marketdata/infrastructure/provider/<vendor>/`.

## Port to implement

`marketdata/application/MarketDataProvider.java`:

- `ProviderInfo info()`: id (matches `MARKET_DATA_PROVIDER`), name, `synthetic = false`, description.
- `List<DailyBar> getHistoricalPrices(String symbol, LocalDate from, LocalDate to)`: oldest first, inclusive range,
  `BigDecimal` prices (do not round beyond the vendor's precision), **adjusted close** populated, volume ≥ 0.
- `Optional<StockQuote> getQuote(String symbol)` and `List<ProviderSearchResult> searchStocks(String query)`.
- Throw `MarketDataProviderException(message, retryable, cause)`: retryable for timeouts, 5xx and 429; not
  retryable for invalid symbols or credentials. Messages end up in the sync status shown to users, so they must be
  client-safe (no URLs with keys, no stack traces).

## Steps

1. **Dependencies** (`backend/pom.xml`): `spring-boot-starter-restclient`; for resilience,
   `io.github.resilience4j:resilience4j-spring-boot4` and Bucket4j (Redis-backed when running several instances);
   for tests, WireMock. Check that versions support Spring Boot 4.
2. **Configuration**: a `@ConfigurationProperties("app.market-data.<vendor>")` record (`baseUrl`, `apiKey`,
   `timeout`, `requestsPerMinute`) with `@Validated`; env vars `MARKET_DATA_API_KEY` etc. in `application.yml`,
   `.env.example` and the README. Never log the key.
3. **Adapter**: `@Component @ConditionalOnProperty(prefix = "app.market-data", name = "provider", havingValue = "<vendor>")`.
   Build a dedicated `RestClient` with connect/read timeouts. Map vendor DTOs (package-private records in the same
   package) to `DailyBar`; parse dates in the exchange's calendar; skip or normalise inconsistent bars (high < low).
4. **Rate limits and resilience**: wait for permits instead of failing (token bucket sized from the vendor plan);
   retry with backoff and jitter only for retryable failures; circuit breaker per provider. The sync service already
   records failures and backs off per stock.
5. **Batching**: if the vendor supports multi-symbol requests, add a batch method behind the port and use it from
   `MarketDataSyncService.syncAll` without changing callers.
6. **Tests** (no real network): WireMock stubs for success, empty range, 429 with `Retry-After`, 5xx, malformed
   JSON, unknown symbol, and timeout. Assert the mapping (dates, adjusted close, decimals) and the exception
   classification. Add an integration test that runs the sync against WireMock and checks stored bars.
7. **Operations**: `prod` must configure a real provider; the `synthetic` provider stays for local demos and tests
   only. Update `docs/architecture.md` §7 and the README provider section.

## Invariants to keep

- No provider calls in the request path; only ingestion (`WatchedStockBackfill`, `MarketDataSyncJob`, start-up
  catch-up) calls the provider.
- Only watched stocks are synchronised (`WatchlistQueries.findAllWatchedStockIds()`).
- Upserts stay idempotent (`(stock_id, bar_date)`), and the trailing re-fetch window picks up corrections.
