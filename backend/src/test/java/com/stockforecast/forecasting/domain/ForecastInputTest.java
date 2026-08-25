package com.stockforecast.forecasting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ForecastInputTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Test
    void exposesOrderedObservationsAndDerivedReturns() {
        ForecastInput input = TestSeries.of(100, 102, 99.96);

        assertThat(input.size()).isEqualTo(3);
        assertThat(input.closes()).containsExactly(100, 102, 99.96);
        assertThat(input.last().close()).isEqualTo(99.96);
        assertThat(input.returns()).hasSize(2);
        assertThat(input.returns().getFirst().simpleReturn()).isCloseTo(0.02, within(1e-12));
        assertThat(input.returns().get(1).logReturn()).isCloseTo(Math.log(99.96 / 102), within(1e-12));
    }

    @Test
    void rejectsDuplicateAndUnorderedObservations() {
        assertThatThrownBy(() -> ForecastInput.of("X", List.of(new PricePoint(MONDAY, 1, 0),
                new PricePoint(MONDAY, 2, 0))))
                .isInstanceOf(InvalidPriceHistoryException.class).hasMessageContaining("Duplicate observation");
        assertThatThrownBy(() -> ForecastInput.of("X", List.of(new PricePoint(MONDAY.plusDays(1), 1, 0),
                new PricePoint(MONDAY, 2, 0))))
                .isInstanceOf(InvalidPriceHistoryException.class).hasMessageContaining("chronological");
    }

    @Test
    void rejectsMissingOrNonPositivePrices() {
        assertThatThrownBy(() -> new PricePoint(MONDAY, 0, 0)).isInstanceOf(InvalidPriceHistoryException.class);
        assertThatThrownBy(() -> new PricePoint(MONDAY, -3, 0)).isInstanceOf(InvalidPriceHistoryException.class);
        assertThatThrownBy(() -> new PricePoint(MONDAY, Double.NaN, 0))
                .isInstanceOf(InvalidPriceHistoryException.class);
        assertThatThrownBy(() -> new PricePoint(MONDAY, Double.POSITIVE_INFINITY, 0))
                .isInstanceOf(InvalidPriceHistoryException.class);
        assertThatThrownBy(() -> new PricePoint(MONDAY, 1, -1)).isInstanceOf(InvalidPriceHistoryException.class);
    }

    @Test
    void truncationKeepsExactlyThePast() {
        ForecastInput input = TestSeries.of(10, 11, 12, 13, 14);

        ForecastInput past = input.truncate(3);

        assertThat(past.closes()).containsExactly(10, 11, 12);
        assertThat(past.last().date()).isEqualTo(input.date(2));
        assertThat(input.truncate(5)).isSameAs(input);
        assertThat(input.tail(2).closes()).containsExactly(13, 14);
        assertThatThrownBy(() -> input.truncate(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsTheLongestCalendarGap() {
        ForecastInput input = ForecastInput.of("X", List.of(new PricePoint(MONDAY, 1, 0),
                new PricePoint(MONDAY.plusDays(1), 1, 0), new PricePoint(MONDAY.plusDays(15), 1, 0)));

        assertThat(input.longestGapDays()).isEqualTo(14);
        assertThat(TestSeries.of(1, 2, 3, 4, 5, 6).longestGapDays()).isEqualTo(3);
    }
}
