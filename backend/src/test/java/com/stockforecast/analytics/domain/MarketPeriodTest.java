package com.stockforecast.analytics.domain;

import com.stockforecast.shared.domain.MarketPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPeriodTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 3, 31);

    @Test
    void parsesCodesCaseInsensitively() {
        assertThat(MarketPeriod.fromCode("ytd")).contains(MarketPeriod.YEAR_TO_DATE);
        assertThat(MarketPeriod.fromCode(" 1y ")).contains(MarketPeriod.ONE_YEAR);
        assertThat(MarketPeriod.fromCode("2Y")).isEmpty();
        assertThat(MarketPeriod.fromCode(null)).isEmpty();
    }

    @Test
    void computesBaseDates() {
        assertThat(MarketPeriod.ONE_WEEK.startDate(AS_OF)).isEqualTo(LocalDate.of(2026, 3, 24));
        assertThat(MarketPeriod.ONE_MONTH.startDate(AS_OF)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(MarketPeriod.YEAR_TO_DATE.startDate(AS_OF)).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(MarketPeriod.ONE_YEAR.startDate(AS_OF)).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(MarketPeriod.FIVE_YEARS.startDate(AS_OF)).isEqualTo(LocalDate.of(2021, 3, 31));
    }

    @Test
    void onlyLongPeriodsUseWeeklyPoints() {
        assertThat(MarketPeriod.FIVE_YEARS.prefersWeeklyPoints()).isTrue();
        assertThat(MarketPeriod.MAX.prefersWeeklyPoints()).isTrue();
        assertThat(MarketPeriod.ONE_YEAR.prefersWeeklyPoints()).isFalse();
    }
}
