package com.stockforecast.forecasting.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One daily observation used for modelling. {@code close} is the split- and dividend-adjusted close, so returns are
 * not distorted by corporate actions.
 */
public record PricePoint(LocalDate date, double close, long volume) {

    public PricePoint {
        Objects.requireNonNull(date, "date");
        if (!(close > 0) || Double.isInfinite(close)) {
            throw new InvalidPriceHistoryException("Price on %s is missing or not positive".formatted(date));
        }
        if (volume < 0) {
            throw new InvalidPriceHistoryException("Volume on %s is negative".formatted(date));
        }
    }
}
