package com.stockforecast.shared.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Look-back periods used by charts and analytics. Returns over a period use the last close on or before
 * {@link #startDate(LocalDate)} as the base.
 */
public enum MarketPeriod {
    ONE_WEEK("1W"),
    FIVE_DAYS("5D"),
    ONE_MONTH("1M"),
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    YEAR_TO_DATE("YTD"),
    ONE_YEAR("1Y"),
    FIVE_YEARS("5Y"),
    MAX("MAX");

    private final String code;

    MarketPeriod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<MarketPeriod> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(period -> period.code.equals(normalized)).findFirst();
    }

    /**
     * Base date for the period ending at {@code asOf}; YTD uses the last close of the previous year.
     */
    public LocalDate startDate(LocalDate asOf) {
        return switch (this) {
            case ONE_WEEK, FIVE_DAYS -> asOf.minusDays(7);
            case ONE_MONTH -> asOf.minusMonths(1);
            case THREE_MONTHS -> asOf.minusMonths(3);
            case SIX_MONTHS -> asOf.minusMonths(6);
            case YEAR_TO_DATE -> LocalDate.of(asOf.getYear() - 1, 12, 31);
            case ONE_YEAR -> asOf.minusYears(1);
            case FIVE_YEARS -> asOf.minusYears(5);
            case MAX -> LocalDate.of(1970, 1, 1);
        };
    }

    /** Long periods are plotted with weekly points to keep payloads small. */
    public boolean prefersWeeklyPoints() {
        return this == FIVE_YEARS || this == MAX;
    }
}
