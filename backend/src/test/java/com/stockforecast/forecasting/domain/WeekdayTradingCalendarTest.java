package com.stockforecast.forecasting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeekdayTradingCalendarTest {

    private final TradingCalendar calendar = new WeekdayTradingCalendar();

    @Test
    void skipsWeekends() {
        LocalDate thursday = LocalDate.of(2026, 10, 8);

        assertThat(calendar.nextTradingDays(thursday, 4)).containsExactly(LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14));
    }

    @Test
    void countsTradingDaysNotCalendarDays() {
        LocalDate friday = LocalDate.of(2026, 10, 9);

        assertThat(calendar.nextTradingDays(friday, 20).getLast()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(calendar.nextTradingDays(friday, 0)).isEmpty();
    }
}
