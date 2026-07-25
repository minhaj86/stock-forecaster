package com.stockforecast.marketdata.infrastructure.provider.synthetic;

import com.stockforecast.marketdata.DailyBar;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticMarketDataProviderTest {

    @Test
    void identicalDatesYieldIdenticalBarsWhateverRangeIsRequested() {
        SyntheticPriceModel model = new SyntheticPriceModel("AAPL");

        List<DailyBar> full = model.bars(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        List<DailyBar> tail = model.bars(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30));

        assertThat(full).endsWith(tail.toArray(DailyBar[]::new));
        assertThat(new SyntheticPriceModel("AAPL").bars(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)))
                .isEqualTo(tail);
    }

    @Test
    void producesConsistentTradingDayBars() {
        List<DailyBar> bars = new SyntheticPriceModel("NVDA").bars(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));

        assertThat(bars).hasSizeGreaterThan(500).allSatisfy(bar -> {
            assertThat(bar.date().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(bar.low().signum()).isPositive();
            assertThat(bar.high()).isGreaterThanOrEqualTo(bar.open().max(bar.close()));
            assertThat(bar.low()).isLessThanOrEqualTo(bar.open().min(bar.close()));
            assertThat(bar.adjustedClose()).isEqualTo(bar.close());
            assertThat(bar.volume()).isPositive();
        });
    }

    @Test
    void staysWithinPlausibleRangesSoDemosLookRealistic() {
        for (String symbol : List.of("AAPL", "MSFT", "NVDA", "KO", "SPY", "BRK.B", "PLTR", "XOM")) {
            List<DailyBar> lastYear = new SyntheticPriceModel(symbol).bars(LocalDate.of(2025, 10, 1), LocalDate.of(2026, 10, 9));
            double[] logReturns = new double[lastYear.size() - 1];
            for (int i = 1; i < lastYear.size(); i++) {
                logReturns[i - 1] = Math.log(lastYear.get(i).close().doubleValue() / lastYear.get(i - 1).close().doubleValue());
            }
            double mean = java.util.Arrays.stream(logReturns).average().orElseThrow();
            double annualVolatility = Math.sqrt(java.util.Arrays.stream(logReturns)
                    .map(r -> (r - mean) * (r - mean)).sum() / (logReturns.length - 1)) * Math.sqrt(252);

            assertThat(lastYear.getLast().close().doubleValue()).as(symbol).isBetween(10.0, 2_000.0);
            assertThat(annualVolatility).as(symbol).isBetween(0.10, 0.60);
        }
    }

    @Test
    void differentSymbolsFollowDifferentPaths() {
        LocalDate day = LocalDate.of(2025, 3, 3);

        assertThat(new SyntheticPriceModel("MSFT").bars(day, day))
                .isNotEqualTo(new SyntheticPriceModel("AAPL").bars(day, day));
    }

    @Test
    void behavesLikeAnEndOfDayFeed() {
        // Monday 10:00 UTC: Monday's bar is not available yet, the latest is Friday's.
        SyntheticMarketDataProvider beforeClose = provider("2026-10-12T10:00:00Z");
        // Monday 22:00 UTC: after the US close.
        SyntheticMarketDataProvider afterClose = provider("2026-10-12T22:00:00Z");

        assertThat(beforeClose.getHistoricalPrices("AAPL", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12))
                .getLast().date()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(afterClose.getHistoricalPrices("AAPL", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12))
                .getLast().date()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(afterClose.info().synthetic()).isTrue();
        assertThat(afterClose.getQuote("AAPL")).hasValueSatisfying(quote ->
                assertThat(quote.price().signum()).isPositive());
        assertThat(afterClose.searchStocks("apple")).isEmpty();
    }

    private static SyntheticMarketDataProvider provider(String now) {
        return new SyntheticMarketDataProvider(Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }
}
