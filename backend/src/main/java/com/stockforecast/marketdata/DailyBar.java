package com.stockforecast.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One trading day of OHLCV data, normalised from any provider. Prices are exact decimals.
 */
public record DailyBar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                       BigDecimal adjustedClose, long volume) {

    public DailyBar {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(adjustedClose, "adjustedClose");
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }
}
