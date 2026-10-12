package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrice;
import com.stockforecast.marketdata.application.MarketDataProvider.HistoricalPrices;
import com.stockforecast.marketdata.application.MarketDataValidationService.ValidationReport;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataValidationServiceTest {

    private static final StockRef AAPL = new StockRef(UUID.randomUUID(), "AAPL", "Apple Inc.", "NASDAQ", "XNAS",
            "USD", "US", "Information Technology", "Hardware", AssetType.COMMON_STOCK);
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 9);

    private final MarketDataValidationService validation = new MarketDataValidationService(
            Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void acceptsConsistentBarsAndNormalisesThemToStoredPrecision() {
        ValidationReport report = validate(response("AAPL", "USD",
                bar("2026-10-02", "101", "103.1234567", "100.5", "102", "101.75", 1_000L),
                bar("2026-10-01", "100", "101", "99", "100.5", "100.25", 900L)));

        assertThat(report.isRejected()).isFalse();
        assertThat(report.violations()).isEmpty();
        assertThat(report.validBars()).extracting(bar -> bar.date().toString())
                .containsExactly("2026-10-01", "2026-10-02");
        assertThat(report.validBars().getLast().high()).isEqualByComparingTo("103.123457");
        assertThat(report.validBars().getLast().high().scale()).isEqualTo(6);
    }

    @Test
    void rejectsInconsistentOrIncompleteBarsIndividually() {
        ValidationReport report = validate(response("AAPL", "USD",
                bar("2026-10-01", "100", "101", "99", "100.5", "100.25", 900L),
                bar("2026-10-02", "100", "98", "99", "100", "100", 900L),
                bar("2026-10-05", "-1", "101", "99", "100", "100", 900L),
                bar("2026-10-06", "100", "101", "99", "102", "102", 900L),
                bar("2026-10-07", "100", "101", "99", "100", null, 900L),
                bar("2026-10-08", "100", "101", "99", "100", "100", null),
                bar("2026-10-09", "100", "101", "99", "100", "100", -5L),
                bar(null, "100", "101", "99", "100", "100", 1L),
                bar("2026-09-30", "100", "101", "99", "100", "100", 1L)));

        assertThat(report.isRejected()).isFalse();
        assertThat(report.validBars()).hasSize(1);
        assertThat(report.violations()).extracting(MarketDataValidationService.Violation::reason).containsExactly(
                "high is below low", "non-positive price", "high is below open or close", "missing price",
                "missing volume", "negative volume", "missing date", "outside the requested range");
        assertThat(report.violationSummary()).startsWith("8 of 9 bars failed validation (2026-10-02: high is below low");
    }

    @Test
    void rejectsFutureBars() {
        ValidationReport report = validation.validate(AAPL, FROM, LocalDate.of(2026, 10, 20),
                MarketDataInterval.ONE_DAY, response("AAPL", "USD",
                        bar("2026-10-12", "100", "101", "99", "100", "100", 1L)));

        assertThat(report.violations()).extracting(MarketDataValidationService.Violation::reason)
                .containsExactly("dated in the future");
    }

    @Test
    void rejectsTheWholeResponseForAnotherInstrumentCurrencyOrDuplicateDates() {
        assertThat(validate(response("MSFT", "USD", bar("2026-10-01", "1", "1", "1", "1", "1", 1L)))
                .rejectionReason()).contains("instead of 'AAPL'");
        assertThat(validate(response("AAPL", "EUR", bar("2026-10-01", "1", "1", "1", "1", "1", 1L)))
                .rejectionReason()).contains("prices in EUR");
        ValidationReport duplicates = validate(response("aapl", null,
                bar("2026-10-01", "1", "1", "1", "1", "1", 1L), bar("2026-10-01", "1", "1", "1", "1", "1", 1L)));
        assertThat(duplicates.rejectionReason()).contains("duplicate bars for 2026-10-01");
        assertThat(duplicates.validBars()).isEmpty();
    }

    @Test
    void acceptsResponsesWithoutInstrumentMetadata() {
        assertThat(validate(response(null, null, bar("2026-10-01", "1", "1", "1", "1", "1", 1L))).validBars())
                .hasSize(1);
    }

    private ValidationReport validate(HistoricalPrices response) {
        return validation.validate(AAPL, FROM, TO, MarketDataInterval.ONE_DAY, response);
    }

    private static HistoricalPrices response(String symbol, String currency, HistoricalPrice... prices) {
        return new HistoricalPrices(symbol, currency, MarketDataInterval.ONE_DAY, List.of(prices));
    }

    static HistoricalPrice bar(String date, String open, String high, String low, String close, String adjusted,
                               Long volume) {
        return new HistoricalPrice(date == null ? null : LocalDate.parse(date), decimal(open), decimal(high),
                decimal(low), decimal(close), decimal(adjusted), volume);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
