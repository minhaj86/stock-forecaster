package com.stockforecast.quant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden values were computed independently (Python, statistics.stdev / Wilder's RSI by hand) for
 * closes 100, 102, 101, 105, 103, 108 on consecutive trading days.
 */
class PriceSeriesTest {

    private static final double TOLERANCE = 1e-12;
    private static final List<LocalDate> DATES = List.of(
            LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7),
            LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 12));
    private static final PriceSeries SERIES = PriceSeries.of(DATES, new double[]{100, 102, 101, 105, 103, 108});

    @Test
    void dailyChangeComparesTheLastTwoCloses() {
        assertThat(SERIES.dailyChange().getAsDouble()).isCloseTo(0.04854368932038833, within(TOLERANCE));
    }

    @Test
    void returnSinceUsesTheLastCloseOnOrBeforeTheStartDate() {
        assertThat(SERIES.returnSince(LocalDate.of(2026, 1, 6)).getAsDouble())
                .isCloseTo(0.05882352941176472, within(TOLERANCE));
        // Saturday 10 Jan has no bar: the base is Friday's close (103).
        assertThat(SERIES.returnSince(LocalDate.of(2026, 1, 10)).getAsDouble())
                .isCloseTo(108.0 / 103 - 1, within(TOLERANCE));
    }

    @Test
    void returnIsUnavailableWhenHistoryStartsAfterTheBaseDate() {
        assertThat(SERIES.returnSince(LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    void rateOfChangeCountsTradingDays() {
        assertThat(SERIES.rateOfChange(3).getAsDouble()).isCloseTo(0.06930693069306937, within(TOLERANCE));
        assertThat(SERIES.rateOfChange(6)).isEmpty();
    }

    @Test
    void annualizedVolatilityUsesSampleStandardDeviationOfLogReturns() {
        assertThat(SERIES.annualizedVolatility(5).getAsDouble()).isCloseTo(0.46485221449963837, within(TOLERANCE));
        assertThat(SERIES.annualizedVolatility(3).getAsDouble()).isCloseTo(0.575496169515924, within(TOLERANCE));
        assertThat(SERIES.annualizedVolatility(6)).isEmpty();
    }

    @Test
    void volatilityOfAConstantPriceIsZero() {
        PriceSeries flat = series(50, 50, 50, 50, 50);

        assertThat(flat.annualizedVolatility(4).getAsDouble()).isZero();
    }

    @Test
    void maxDrawdownIsTheDeepestFallFromARunningPeak() {
        assertThat(SERIES.maxDrawdownSince(LocalDate.of(2026, 1, 5)).getAsDouble())
                .isCloseTo(-0.01904761904761909, within(TOLERANCE));
        assertThat(series(10, 11, 12).maxDrawdownSince(LocalDate.of(2026, 1, 5)).getAsDouble()).isZero();
    }

    @Test
    void rsiFollowsWildersDefinition() {
        assertThat(SERIES.rsi(3).getAsDouble()).isCloseTo(81.17647058823529, within(1e-9));
        assertThat(SERIES.rsi(2).getAsDouble()).isCloseTo(84.7457627118644, within(1e-9));
        assertThat(series(1, 2, 3, 4).rsi(3).getAsDouble()).isEqualTo(100);
        assertThat(series(5, 5, 5, 5).rsi(3).getAsDouble()).isEqualTo(50);
        assertThat(SERIES.rsi(6)).isEmpty();
    }

    @Test
    void normalisedSeriesStartsAtOneHundred() {
        NormalizedSeries normalized = SERIES.normalizedSince(LocalDate.of(2026, 1, 6), false);

        assertThat(normalized.partial()).isFalse();
        assertThat(normalized.baseDate()).isEqualTo(LocalDate.of(2026, 1, 6));
        assertThat(normalized.points()).extracting(NormalizedSeries.Point::value)
                .containsExactly(100.0, 100 * 101.0 / 102, 100 * 105.0 / 102, 100 * 103.0 / 102, 100 * 108.0 / 102);
        assertThat(normalized.totalReturn()).isCloseTo(108.0 / 102 - 1, within(TOLERANCE));
    }

    @Test
    void normalisedSeriesIsFlaggedPartialWhenHistoryIsShorterThanThePeriod() {
        NormalizedSeries normalized = SERIES.normalizedSince(LocalDate.of(2025, 6, 1), false);

        assertThat(normalized.partial()).isTrue();
        assertThat(normalized.baseDate()).isEqualTo(DATES.getFirst());
        assertThat(normalized.points()).hasSize(6);
    }

    @Test
    void weeklySamplingKeepsTheLastTradingDayOfEachWeekPlusBaseAndLatestPoints() {
        List<LocalDate> threeWeeks = IntStream.range(0, 21).mapToObj(i -> LocalDate.of(2026, 3, 2).plusDays(i))
                .filter(date -> date.getDayOfWeek().getValue() <= 5).toList();
        double[] closes = IntStream.range(0, threeWeeks.size()).mapToDouble(i -> 100 + i).toArray();

        NormalizedSeries weekly = PriceSeries.of(threeWeeks, closes).normalizedSince(LocalDate.of(2026, 3, 2), true);

        assertThat(weekly.points()).extracting(NormalizedSeries.Point::date).containsExactly(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 13),
                LocalDate.of(2026, 3, 20));
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> PriceSeries.of(List.of(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)),
                new double[]{1, 2})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceSeries.of(List.of(LocalDate.of(2026, 1, 2)), new double[]{0}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceSeries.of(List.of(LocalDate.of(2026, 1, 2)), new double[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PriceSeries series(double... closes) {
        return PriceSeries.of(DATES.subList(0, closes.length), closes);
    }
}
