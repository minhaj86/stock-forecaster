package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataProviders.ProviderUsage;
import com.stockforecast.marketdata.MarketDataProviders.RateLimitStatus;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.ProviderInstrumentMatch;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrice;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrices;
import com.stockforecast.marketdata.application.MarketDataProvider.ProviderInstrument;
import com.stockforecast.marketdata.application.MarketDataProviderException;
import com.stockforecast.marketdata.application.ProviderErrorCode;
import com.stockforecast.marketdata.application.ProviderThrottle;
import com.stockforecast.marketdata.application.ProviderUsageRecorder;
import com.stockforecast.marketdata.infrastructure.provider.Bucket4jProviderThrottle;
import com.stockforecast.stock.AssetType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter against a stubbed Twelve Data API (never the real one): payload mapping, price adjustment, error
 * classification, rate limits and timeouts.
 */
class TwelveDataMarketDataProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");
    private static final String API_KEY = "test-key-123";
    private static final ProviderInstrument AAPL = new ProviderInstrument("AAPL", null);
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 9);

    private WireMockServer server;
    private RecordingUsage usage;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        usage = new RecordingUsage();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void mapsSplitAdjustedTradePricesAndTotalReturnAdjustedCloses() {
        stubTimeSeries("splits", okJson(series("""
                {"datetime": "2026-10-09", "open": "255.90", "high": "258.00", "low": "255.00", "close": "257.30",
                 "volume": "38000000"},
                {"datetime": "2026-10-08", "open": "254.10", "high": "256.50", "low": "253.20", "close": "255.80",
                 "volume": "41000000"}""")));
        stubTimeSeries("all", okJson(series("""
                {"datetime": "2026-10-08", "open": "253.9", "high": "256.3", "low": "253.0", "close": "255.55",
                 "volume": "41000000"},
                {"datetime": "2026-10-09", "open": "255.7", "high": "257.8", "low": "254.8", "close": "257.05",
                 "volume": "38000000"}""")));

        HistoricalPrices prices = provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY);

        assertThat(prices.symbol()).isEqualTo("AAPL");
        assertThat(prices.currency()).isEqualTo("USD");
        assertThat(prices.prices()).hasSize(2);
        assertThat(prices.prices().getFirst()).satisfies(bar -> {
            assertThat(bar.date()).isEqualTo(LocalDate.of(2026, 10, 8));
            assertThat(bar.open()).isEqualByComparingTo("254.10");
            assertThat(bar.close()).isEqualByComparingTo("255.80");
            assertThat(bar.adjustedClose()).isEqualByComparingTo("255.55");
            assertThat(bar.volume()).isEqualTo(41_000_000L);
        });
        // The key travels in the Authorization header, never in the URL.
        server.verify(2, getRequestedFor(urlPathEqualTo("/time_series"))
                .withHeader("Authorization", equalTo("apikey " + API_KEY))
                .withQueryParam("apikey", absent())
                .withQueryParam("interval", equalTo("1day"))
                .withQueryParam("start_date", equalTo("2026-10-01"))
                .withQueryParam("order", equalTo("asc")));
        assertThat(usage.successes).isEqualTo(2);
    }

    @Test
    void anUnfinishedSessionIsNotReturned() {
        Clock duringTrading = Clock.fixed(Instant.parse("2026-10-09T17:00:00Z"), ZoneOffset.UTC);
        String bars = """
                {"datetime": "2026-10-08", "open": "1", "high": "1", "low": "1", "close": "1", "volume": "1"},
                {"datetime": "2026-10-09", "open": "1", "high": "1", "low": "1", "close": "1", "volume": "1"}""";
        stubTimeSeries("splits", okJson(series(bars)));
        stubTimeSeries("all", okJson(series(bars)));

        HistoricalPrices prices = provider(properties(API_KEY, 5000), duringTrading)
                .getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY);

        assertThat(prices.prices()).extracting(HistoricalPrice::date).containsExactly(LocalDate.of(2026, 10, 8));
    }

    @Test
    void treatsAnEmptyRangeAsNoData() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code": 400, "message": "No data is available on the specified dates. Try setting \
                        different start/end dates.", "status": "error"}""")));

        assertThat(provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY).prices()).isEmpty();
    }

    @Test
    void rejectedCredentialsAreAPermanentFailureWithAClientSafeMessage() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code": 401, "message": "**apikey** parameter is incorrect or not specified. You can get \
                        your free API key instantly following this link: https://twelvedata.com/pricing",
                         "status": "error"}""")));

        assertThatThrownBy(() -> provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo(ProviderErrorCode.AUTHENTICATION_FAILED);
                    assertThat(error.isRetryable()).isFalse();
                    assertThat(error.getMessage()).doesNotContain("https://").doesNotContain("**")
                            .doesNotContain(API_KEY);
                });
        assertThat(usage.failures).containsExactly(ProviderErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    void errorPayloadsAreDetectedEvenWithHttp200() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(okJson("""
                {"code": 400, "message": "**symbol** not found: XYZ. Please specify it correctly.", "status": "error"}
                """)));

        assertThatThrownBy(() -> provider().getHistoricalPrices(new ProviderInstrument("XYZ", null), FROM, TO,
                MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error ->
                        assertThat(error.code()).isEqualTo(ProviderErrorCode.SYMBOL_NOT_FOUND));
    }

    @Test
    void unknownSymbolsAreNotRetried() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\": 404, \"message\": \"Data not found\", \"status\": \"error\"}")));

        assertThatThrownBy(() -> provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo(ProviderErrorCode.SYMBOL_NOT_FOUND);
                    assertThat(error.isRetryable()).isFalse();
                });
    }

    @Test
    void aListingUnknownUnderItsMarketIdentifierIsRetriedWithoutIt() {
        server.stubFor(get(urlPathEqualTo("/time_series")).withQueryParam("mic_code", equalTo("XNAS"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\": 404, \"message\": \"Data not found\", \"status\": \"error\"}")));
        server.stubFor(get(urlPathEqualTo("/time_series")).withQueryParam("mic_code", absent())
                .willReturn(okJson(series(""))));

        assertThat(provider().getHistoricalPrices(new ProviderInstrument("AAPL", "XNAS"), FROM, TO,
                MarketDataInterval.ONE_DAY).prices()).isEmpty();
        server.verify(1, getRequestedFor(urlPathEqualTo("/time_series")).withQueryParam("mic_code", absent()));
    }

    @Test
    void rateLimitsAreRetryableAndHonourRetryAfter() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "30")
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code": 429, "message": "You have run out of API credits for the current minute.",
                         "status": "error"}""")));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderThrottle throttle = new Bucket4jProviderThrottle(TwelveDataClient.PROVIDER_ID, 1000, clock,
                new SimpleMeterRegistry());

        assertThatThrownBy(() -> provider(properties(API_KEY, 5000), clock, throttle)
                .getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo(ProviderErrorCode.RATE_LIMITED);
                    assertThat(error.isRetryable()).isTrue();
                    assertThat(error.retryAfter()).contains(Duration.ofSeconds(30));
                });
        assertThat(throttle.status().throttledUntil()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void exhaustedDailyQuotasWaitUntilTheNextDay() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code": 429, "message": "You have run out of API credits for the day.", "status": "error"}
                        """)));

        assertThatThrownBy(() -> provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error ->
                        assertThat(error.retryAfter()).contains(Duration.ofMinutes(121)));
    }

    @Test
    void serverErrorsAreTransient() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo(ProviderErrorCode.UNAVAILABLE);
                    assertThat(error.isRetryable()).isTrue();
                });
    }

    @Test
    void timeoutsAreTransient() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(okJson(series("")).withFixedDelay(1_500)));

        assertThatThrownBy(() -> provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo(ProviderErrorCode.TIMEOUT);
                    assertThat(error.isRetryable()).isTrue();
                });
    }

    @Test
    void malformedPayloadsAreRejected() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"meta\": {\"symbol\": \"AAPL\"")));

        assertThatThrownBy(() -> provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo(ProviderErrorCode.INVALID_RESPONSE);
                    assertThat(error.isRetryable()).isFalse();
                });
    }

    @Test
    void unparseableValuesReachValidationAsMissing() {
        stubTimeSeries("splits", okJson(series("""
                {"datetime": "2026-10-08", "open": "n/a", "high": "256.50", "low": "253.20", "close": "255.80",
                 "volume": ""}""")));
        stubTimeSeries("all", okJson(series("")));

        HistoricalPrices prices = provider().getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY);

        assertThat(prices.prices().getFirst()).satisfies(bar -> {
            assertThat(bar.open()).isNull();
            assertThat(bar.volume()).isNull();
            assertThat(bar.adjustedClose()).isNull();
        });
    }

    @Test
    void longRangesAreSplitIntoSeveralRequests() {
        server.stubFor(get(urlPathEqualTo("/time_series")).willReturn(okJson(series(""))));

        provider(properties(API_KEY, 100), Clock.fixed(NOW, ZoneOffset.UTC))
                .getHistoricalPrices(AAPL, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                        MarketDataInterval.ONE_DAY);

        // 365 days in chunks of 126 days; chunks without trading data skip the adjusted-close request.
        server.verify(3, getRequestedFor(urlPathEqualTo("/time_series")).withQueryParam("adjust", equalTo("splits")));
        server.verify(0, getRequestedFor(urlPathEqualTo("/time_series")).withQueryParam("adjust", equalTo("all")));
    }

    @Test
    void searchNormalisesInstrumentsForTheCatalogue() {
        server.stubFor(get(urlPathEqualTo("/symbol_search")).withQueryParam("symbol", equalTo("Microsoft"))
                .willReturn(okJson("""
                        {"data": [
                          {"symbol": "MSFT", "instrument_name": "Microsoft Corporation", "exchange": "NASDAQ",
                           "mic_code": "XNGS", "exchange_timezone": "America/New_York",
                           "instrument_type": "Common Stock", "country": "United States", "currency": "USD"},
                          {"symbol": "MSFU", "instrument_name": "Direxion Daily MSFT Bull 2X", "exchange": "NASDAQ",
                           "mic_code": "XNMS", "instrument_type": "ETF", "country": "United States", "currency": "USD"},
                          {"symbol": "MSF", "instrument_name": "Microsoft Corp", "exchange": "XETR", "mic_code": "XETR",
                           "instrument_type": "Warrant", "country": "Germany", "currency": "EUR"}],
                         "status": "ok"}""")));

        List<ProviderInstrumentMatch> matches = provider().searchStocks("Microsoft");

        assertThat(matches).extracting(ProviderInstrumentMatch::symbol).containsExactly("MSFT", "MSFU", "MSF");
        assertThat(matches.getFirst()).satisfies(match -> {
            assertThat(match.country()).isEqualTo("US");
            assertThat(match.mic()).isEqualTo("XNGS");
            assertThat(match.assetType()).isEqualTo(AssetType.COMMON_STOCK);
        });
        assertThat(matches.get(1).assetType()).isEqualTo(AssetType.ETF);
        assertThat(matches.get(2).assetType()).isNull();
        assertThat(matches.get(2).country()).isEqualTo("DE");
    }

    @Test
    void metadataPicksTheRequestedListing() {
        server.stubFor(get(urlPathEqualTo("/symbol_search")).willReturn(okJson("""
                {"data": [
                  {"symbol": "SAP", "instrument_name": "SAP SE", "exchange": "NYSE", "mic_code": "XNYS",
                   "instrument_type": "American Depositary Receipt", "country": "United States", "currency": "USD"},
                  {"symbol": "SAP", "instrument_name": "SAP SE", "exchange": "XETR", "mic_code": "XETR",
                   "instrument_type": "Common Stock", "country": "Germany", "currency": "EUR"}],
                 "status": "ok"}""")));

        assertThat(provider().getStockMetadata(new ProviderInstrument("SAP", "XETR"))).hasValueSatisfying(stock -> {
            assertThat(stock.currency()).isEqualTo("EUR");
            assertThat(stock.country()).isEqualTo("DE");
            assertThat(stock.assetType()).isEqualTo(AssetType.COMMON_STOCK);
        });
        assertThat(provider().getStockMetadata(new ProviderInstrument("SAP", "XLON"))).isEmpty();
    }

    @Test
    void healthReflectsThePlanUsage() {
        server.stubFor(get(urlPathEqualTo("/api_usage")).willReturn(okJson("""
                {"timestamp": "2026-10-11 22:00:00", "current_usage": 3, "plan_limit": 8, "daily_usage": 120,
                 "plan_daily_limit": 800, "plan_category": "basic"}""")));

        ProviderHealth health = provider().checkHealth();

        assertThat(health.status()).isEqualTo(ProviderHealth.Status.HEALTHY);
        assertThat(health.message()).isEqualTo("Provider reachable; 3 of 8 requests used this minute");
        assertThat(health.details()).containsEntry("dailyLimit", 800).containsEntry("planCategory", "basic");
    }

    @Test
    void anExhaustedMinuteQuotaDegradesTheHealth() {
        server.stubFor(get(urlPathEqualTo("/api_usage")).willReturn(okJson("""
                {"current_usage": 8, "plan_limit": 8, "plan_category": "basic"}""")));

        assertThat(provider().checkHealth().status()).isEqualTo(ProviderHealth.Status.DEGRADED);
    }

    @Test
    void aMissingApiKeyIsReportedWithoutCallingTheProvider() {
        TwelveDataMarketDataProvider provider = provider(properties(" ", 5000), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(provider.isConfigured()).isFalse();
        assertThatThrownBy(() -> provider.getHistoricalPrices(AAPL, FROM, TO, MarketDataInterval.ONE_DAY))
                .isInstanceOfSatisfying(MarketDataProviderException.class, error ->
                        assertThat(error.code()).isEqualTo(ProviderErrorCode.NOT_CONFIGURED));
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void quotesAreConvertedToDecimalFractions() {
        server.stubFor(get(urlPathEqualTo("/quote")).willReturn(okJson("""
                {"symbol": "AAPL", "close": "257.30", "change": "1.50", "percent_change": "0.58642",
                 "timestamp": 1791576000}""")));

        assertThat(provider().getLatestQuote(AAPL)).hasValueSatisfying(quote -> {
            assertThat(quote.price()).isEqualByComparingTo("257.30");
            assertThat(quote.changePercent()).isEqualByComparingTo(new BigDecimal("0.00586420"));
        });
    }

    private void stubTimeSeries(String adjust, ResponseDefinitionBuilder response) {
        server.stubFor(get(urlPathEqualTo("/time_series")).withQueryParam("adjust", equalTo(adjust))
                .willReturn(response));
    }

    private static String series(String values) {
        return """
                {"meta": {"symbol": "AAPL", "interval": "1day", "currency": "USD",
                          "exchange_timezone": "America/New_York", "exchange": "NASDAQ", "mic_code": "XNGS",
                          "type": "Common Stock"},
                 "values": [%s],
                 "status": "ok"}""".formatted(values);
    }

    private TwelveDataMarketDataProvider provider() {
        return provider(properties(API_KEY, 5000), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TwelveDataMarketDataProvider provider(TwelveDataProperties properties, Clock clock) {
        return provider(properties, clock, new Bucket4jProviderThrottle(TwelveDataClient.PROVIDER_ID,
                properties.requestsPerMinute(), clock, new SimpleMeterRegistry()));
    }

    private TwelveDataMarketDataProvider provider(TwelveDataProperties properties, Clock clock,
                                                  ProviderThrottle throttle) {
        TwelveDataClient client = new TwelveDataConfiguration().twelveDataClient(RestClient.builder(),
                JsonMapper.builder().build(), properties, throttle, usage, new SimpleMeterRegistry(), clock);
        return new TwelveDataMarketDataProvider(client, properties, clock);
    }

    private TwelveDataProperties properties(String apiKey, int maxBarsPerRequest) {
        return new TwelveDataProperties(server.baseUrl(), apiKey, Duration.ofSeconds(1), Duration.ofMillis(500),
                1000, Duration.ofSeconds(1), maxBarsPerRequest);
    }

    private static final class RecordingUsage implements ProviderUsageRecorder {

        private int successes;
        private final List<ProviderErrorCode> failures = new ArrayList<>();

        @Override
        public void recordSuccess(String provider) {
            successes++;
        }

        @Override
        public void recordFailure(String provider, ProviderErrorCode code, String message) {
            failures.add(code);
        }

        @Override
        public ProviderUsage today(String provider, RateLimitStatus rateLimit) {
            return new ProviderUsage(successes + failures.size(), failures.size(), 0, null, null, null, rateLimit);
        }
    }
}
