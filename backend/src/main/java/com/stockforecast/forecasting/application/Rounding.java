package com.stockforecast.forecasting.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Output rounding: prices to 4 decimals, ratios to 6, fitted values to 6 significant digits. */
final class Rounding {

    private Rounding() {
    }

    static double price(double value) {
        return scaled(value, 4);
    }

    static double ratio(double value) {
        return scaled(value, 6);
    }

    static Double ratio(Double value) {
        return value == null ? null : ratio(value.doubleValue());
    }

    static double significant(double value) {
        return new BigDecimal(value).round(new MathContext(6, RoundingMode.HALF_UP)).doubleValue();
    }

    private static double scaled(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
