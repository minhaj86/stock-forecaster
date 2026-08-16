package com.stockforecast.forecasting.domain.backtest;

import java.time.LocalDate;

/**
 * One evaluated forecast: made at {@code originDate} with data up to that day, checked against the outcome at
 * {@code targetDate}. {@code actual}, {@code predicted}, {@code lower} and {@code upper} are in target units (price or
 * return); the prices are kept to evaluate direction.
 */
public record BacktestObservation(LocalDate originDate, LocalDate targetDate, double originPrice, double actualPrice,
                                  double predictedPrice, double actual, double predicted, double lower,
                                  double upper) {

    public double error() {
        return actual - predicted;
    }

    public boolean withinRange() {
        return actual >= lower && actual <= upper;
    }
}
