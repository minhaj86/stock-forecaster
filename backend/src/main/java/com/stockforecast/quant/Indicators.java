package com.stockforecast.quant;

import java.util.Arrays;
import java.util.Objects;

/**
 * Trailing technical indicators as series aligned with their input: the value at index {@code t} is computed from
 * observations {@code 0..t} only, never from later ones, so the series can be used for time-series modelling without
 * look-ahead. Positions where an indicator is not yet defined (warm-up) hold {@link Double#NaN}.
 *
 * <ul>
 *     <li>Simple return: {@code P(t) / P(t-1) - 1}; log return: {@code ln(P(t) / P(t-1))}.</li>
 *     <li>Rate of change (momentum): {@code P(t) / P(t-n) - 1}.</li>
 *     <li>SMA: arithmetic mean of the last {@code n} values.</li>
 *     <li>EMA: {@code a = 2 / (span + 1)}, seeded with the SMA of the first {@code span} values (TA-Lib convention).</li>
 *     <li>RSI: Wilder's relative strength index (SMA seed of gains/losses, then Wilder smoothing); first value at
 *     index {@code period}.</li>
 *     <li>Volatility: sample standard deviation of the last {@code n} log returns, annualised with &radic;252.</li>
 * </ul>
 */
public final class Indicators {

    public static final int TRADING_DAYS_PER_YEAR = 252;

    private Indicators() {
    }

    public static double[] simpleReturns(double[] closes) {
        double[] result = nanArray(closes.length);
        for (int t = 1; t < closes.length; t++) {
            result[t] = closes[t] / closes[t - 1] - 1;
        }
        return result;
    }

    public static double[] logReturns(double[] closes) {
        double[] result = nanArray(closes.length);
        for (int t = 1; t < closes.length; t++) {
            result[t] = Math.log(closes[t] / closes[t - 1]);
        }
        return result;
    }

    public static double[] rateOfChange(double[] closes, int periods) {
        requirePositive(periods, "periods");
        double[] result = nanArray(closes.length);
        for (int t = periods; t < closes.length; t++) {
            result[t] = closes[t] / closes[t - periods] - 1;
        }
        return result;
    }

    public static double[] sma(double[] values, int window) {
        requirePositive(window, "window");
        double[] result = nanArray(values.length);
        for (int t = window - 1; t < values.length; t++) {
            double sum = 0;
            for (int i = t - window + 1; i <= t; i++) {
                sum += values[i];
            }
            result[t] = sum / window;
        }
        return result;
    }

    public static double[] ema(double[] values, int span) {
        requirePositive(span, "span");
        double[] result = nanArray(values.length);
        if (values.length < span) {
            return result;
        }
        double alpha = 2.0 / (span + 1);
        double seed = 0;
        for (int i = 0; i < span; i++) {
            seed += values[i];
        }
        result[span - 1] = seed / span;
        for (int t = span; t < values.length; t++) {
            result[t] = alpha * values[t] + (1 - alpha) * result[t - 1];
        }
        return result;
    }

    public static double[] rsi(double[] closes, int period) {
        requirePositive(period, "period");
        double[] result = nanArray(closes.length);
        if (closes.length <= period) {
            return result;
        }
        double gains = 0;
        double losses = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            gains += Math.max(change, 0);
            losses += Math.max(-change, 0);
        }
        double averageGain = gains / period;
        double averageLoss = losses / period;
        result[period] = rsiValue(averageGain, averageLoss);
        for (int t = period + 1; t < closes.length; t++) {
            double change = closes[t] - closes[t - 1];
            averageGain = (averageGain * (period - 1) + Math.max(change, 0)) / period;
            averageLoss = (averageLoss * (period - 1) + Math.max(-change, 0)) / period;
            result[t] = rsiValue(averageGain, averageLoss);
        }
        return result;
    }

    /**
     * Annualised volatility of the last {@code window} daily log returns (needs {@code window + 1} closes).
     */
    public static double[] rollingVolatility(double[] closes, int window) {
        if (window < 2) {
            throw new IllegalArgumentException("window must be at least 2");
        }
        double[] logReturns = logReturns(closes);
        double[] result = nanArray(closes.length);
        double annualisation = Math.sqrt(TRADING_DAYS_PER_YEAR);
        for (int t = window; t < closes.length; t++) {
            double mean = 0;
            for (int i = t - window + 1; i <= t; i++) {
                mean += logReturns[i];
            }
            mean /= window;
            double sumOfSquares = 0;
            for (int i = t - window + 1; i <= t; i++) {
                double deviation = logReturns[i] - mean;
                sumOfSquares += deviation * deviation;
            }
            result[t] = Math.sqrt(sumOfSquares / (window - 1)) * annualisation;
        }
        return result;
    }

    public static double[] rollingMax(double[] values, int window) {
        requirePositive(window, "window");
        double[] result = nanArray(values.length);
        for (int t = window - 1; t < values.length; t++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int i = t - window + 1; i <= t; i++) {
                max = Math.max(max, values[i]);
            }
            result[t] = max;
        }
        return result;
    }

    public static double[] rollingMin(double[] values, int window) {
        requirePositive(window, "window");
        double[] result = nanArray(values.length);
        for (int t = window - 1; t < values.length; t++) {
            double min = Double.POSITIVE_INFINITY;
            for (int i = t - window + 1; i <= t; i++) {
                min = Math.min(min, values[i]);
            }
            result[t] = min;
        }
        return result;
    }

    /** Element-wise {@code numerator / denominator}; NaN where either side is undefined. */
    public static double[] ratio(double[] numerator, double[] denominator) {
        if (numerator.length != denominator.length) {
            throw new IllegalArgumentException("series must have the same length");
        }
        double[] result = new double[numerator.length];
        for (int t = 0; t < result.length; t++) {
            result[t] = numerator[t] / denominator[t];
        }
        return result;
    }

    /** The last element, or NaN for an empty series. */
    public static double last(double[] series) {
        Objects.requireNonNull(series, "series");
        return series.length == 0 ? Double.NaN : series[series.length - 1];
    }

    private static double rsiValue(double averageGain, double averageLoss) {
        if (averageLoss == 0) {
            return averageGain == 0 ? 50 : 100;
        }
        return 100 - 100 / (1 + averageGain / averageLoss);
    }

    private static double[] nanArray(int length) {
        double[] result = new double[length];
        Arrays.fill(result, Double.NaN);
        return result;
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1");
        }
    }
}
