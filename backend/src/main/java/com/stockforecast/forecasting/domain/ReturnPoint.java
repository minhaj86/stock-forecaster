package com.stockforecast.forecasting.domain;

import java.time.LocalDate;

/**
 * Return from the previous observation to {@code date}.
 */
public record ReturnPoint(LocalDate date, double simpleReturn, double logReturn) {
}
