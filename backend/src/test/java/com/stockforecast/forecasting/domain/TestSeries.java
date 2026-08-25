package com.stockforecast.forecasting.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Deterministic price histories on consecutive weekdays for forecasting tests.
 */
public final class TestSeries {

    public static final LocalDate START = LocalDate.of(2024, 1, 1);

    private TestSeries() {
    }

    public static ForecastInput of(double... closes) {
        List<PricePoint> points = new ArrayList<>(closes.length);
        List<LocalDate> dates = weekdays(closes.length);
        for (int i = 0; i < closes.length; i++) {
            points.add(new PricePoint(dates.get(i), closes[i], 1_000_000 + 1_000L * i));
        }
        return ForecastInput.of("TEST", points);
    }

    /** Geometric random walk: {@code ln P(t) - ln P(t-1) ~ N(drift, volatility²)}, reproducible from the seed. */
    public static double[] randomWalk(int length, double start, double drift, double volatility, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        double[] closes = new double[length];
        closes[0] = start;
        for (int i = 1; i < length; i++) {
            closes[i] = closes[i - 1] * Math.exp(drift + volatility * gaussian(random));
        }
        return closes;
    }

    /** Exact exponential growth {@code start * exp(rate * t)}. */
    public static double[] exponentialGrowth(int length, double start, double rate) {
        double[] closes = new double[length];
        for (int i = 0; i < length; i++) {
            closes[i] = start * Math.exp(rate * i);
        }
        return closes;
    }

    public static List<LocalDate> weekdays(int count) {
        List<LocalDate> dates = new ArrayList<>(count);
        LocalDate date = START;
        while (dates.size() < count) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return dates;
    }

    private static double gaussian(SplittableRandom random) {
        // Box-Muller; avoids java.util.Random's synchronisation and is stable across JDKs.
        double u1 = random.nextDouble();
        double u2 = random.nextDouble();
        return Math.sqrt(-2 * Math.log(1 - u1)) * Math.cos(2 * Math.PI * u2);
    }
}
