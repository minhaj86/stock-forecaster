package com.stockforecast.forecasting.domain;

import com.stockforecast.quant.Indicators;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Validated, immutable daily history of one stock (oldest first), independent of persistence. Dates are strictly
 * ascending (no duplicates) and every close is positive and finite.
 *
 * <p>{@link #truncate(int)} is how backtests hide the future: a model only ever receives the prefix of the history
 * that was known at the forecast origin.
 */
public final class ForecastInput {

    private final String symbol;
    private final LocalDate[] dates;
    private final double[] closes;
    private final long[] volumes;

    private ForecastInput(String symbol, LocalDate[] dates, double[] closes, long[] volumes) {
        this.symbol = symbol;
        this.dates = dates;
        this.closes = closes;
        this.volumes = volumes;
    }

    public static ForecastInput of(String symbol, List<PricePoint> prices) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(prices, "prices");
        int n = prices.size();
        LocalDate[] dates = new LocalDate[n];
        double[] closes = new double[n];
        long[] volumes = new long[n];
        for (int i = 0; i < n; i++) {
            PricePoint point = Objects.requireNonNull(prices.get(i), "price point");
            if (i > 0 && !point.date().isAfter(dates[i - 1])) {
                throw new InvalidPriceHistoryException(point.date().equals(dates[i - 1])
                        ? "Duplicate observation on " + point.date()
                        : "Observations are not in chronological order at " + point.date());
            }
            dates[i] = point.date();
            closes[i] = point.close();
            volumes[i] = point.volume();
        }
        return new ForecastInput(symbol, dates, closes, volumes);
    }

    public String symbol() {
        return symbol;
    }

    public int size() {
        return closes.length;
    }

    public boolean isEmpty() {
        return closes.length == 0;
    }

    public LocalDate date(int index) {
        return dates[index];
    }

    public double close(int index) {
        return closes[index];
    }

    public PricePoint last() {
        requireNotEmpty();
        return point(closes.length - 1);
    }

    public PricePoint point(int index) {
        return new PricePoint(dates[index], closes[index], volumes[index]);
    }

    /** A read-only view of the observations. */
    public List<PricePoint> prices() {
        return new AbstractList<>() {
            @Override
            public PricePoint get(int index) {
                return point(index);
            }

            @Override
            public int size() {
                return closes.length;
            }
        };
    }

    /** A copy of the closes (oldest first). */
    public double[] closes() {
        return closes.clone();
    }

    public double[] volumes() {
        return Arrays.stream(volumes).asDoubleStream().toArray();
    }

    public List<LocalDate> dates() {
        return List.of(dates);
    }

    /** Daily returns; the first observation has no return and is skipped. */
    public List<ReturnPoint> returns() {
        double[] simple = Indicators.simpleReturns(closes);
        double[] log = Indicators.logReturns(closes);
        List<ReturnPoint> result = new ArrayList<>(Math.max(0, closes.length - 1));
        for (int i = 1; i < closes.length; i++) {
            result.add(new ReturnPoint(dates[i], simple[i], log[i]));
        }
        return result;
    }

    /**
     * The first {@code length} observations: exactly what was known at observation {@code length - 1}.
     */
    public ForecastInput truncate(int length) {
        if (length < 0 || length > closes.length) {
            throw new IllegalArgumentException("length must be between 0 and " + closes.length);
        }
        if (length == closes.length) {
            return this;
        }
        return new ForecastInput(symbol, Arrays.copyOf(dates, length), Arrays.copyOf(closes, length),
                Arrays.copyOf(volumes, length));
    }

    /** The most recent {@code count} observations (or all of them). */
    public ForecastInput tail(int count) {
        int from = Math.max(0, closes.length - count);
        return from == 0 ? this : new ForecastInput(symbol, Arrays.copyOfRange(dates, from, dates.length),
                Arrays.copyOfRange(closes, from, closes.length), Arrays.copyOfRange(volumes, from, volumes.length));
    }

    /**
     * The longest calendar gap between consecutive observations, in days (weekends make 3 normal); 0 when there are
     * fewer than two observations.
     */
    public long longestGapDays() {
        long longest = 0;
        for (int i = 1; i < dates.length; i++) {
            longest = Math.max(longest, ChronoUnit.DAYS.between(dates[i - 1], dates[i]));
        }
        return longest;
    }

    private void requireNotEmpty() {
        if (closes.length == 0) {
            throw new IllegalStateException("forecast input is empty");
        }
    }
}
