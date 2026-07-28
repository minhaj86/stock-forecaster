package com.stockforecast.quant;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable daily close series (oldest first) with the platform's metric definitions. Every method returns
 * empty when the history is too short, rather than a misleading number.
 *
 * <ul>
 *     <li>Return since a date: {@code P(last) / P(base) - 1}, base = last close on or before that date.</li>
 *     <li>Volatility: sample standard deviation of the last n daily log returns, annualised with √252.</li>
 *     <li>Maximum drawdown: {@code min(P(t) / running peak - 1)} from the base date (≤ 0).</li>
 *     <li>RSI: Wilder's relative strength index (SMA seed, then Wilder smoothing).</li>
 *     <li>Rate of change: {@code P(last) / P(last - n) - 1} over n trading days.</li>
 * </ul>
 *
 * <p>Volatility and RSI are the last values of the corresponding {@link Indicators} series, so analytics and
 * forecasting features share one implementation.
 */
public final class PriceSeries {

    public static final int TRADING_DAYS_PER_YEAR = Indicators.TRADING_DAYS_PER_YEAR;

    private final LocalDate[] dates;
    private final double[] closes;

    private PriceSeries(LocalDate[] dates, double[] closes) {
        this.dates = dates;
        this.closes = closes;
    }

    public static PriceSeries of(List<LocalDate> dates, double[] closes) {
        Objects.requireNonNull(dates, "dates");
        Objects.requireNonNull(closes, "closes");
        if (dates.size() != closes.length) {
            throw new IllegalArgumentException("dates and closes must have the same length");
        }
        LocalDate[] dateArray = dates.toArray(LocalDate[]::new);
        for (int i = 0; i < closes.length; i++) {
            if (!(closes[i] > 0) || Double.isInfinite(closes[i])) {
                throw new IllegalArgumentException("closes must be positive and finite");
            }
            if (i > 0 && !dateArray[i].isAfter(dateArray[i - 1])) {
                throw new IllegalArgumentException("dates must be strictly ascending");
            }
        }
        return new PriceSeries(dateArray, closes.clone());
    }

    public int size() {
        return closes.length;
    }

    public boolean isEmpty() {
        return closes.length == 0;
    }

    public LocalDate lastDate() {
        requireNotEmpty();
        return dates[dates.length - 1];
    }

    public double lastClose() {
        requireNotEmpty();
        return closes[closes.length - 1];
    }

    /** Change of the last close versus the previous one. */
    public OptionalDouble dailyChange() {
        return rateOfChange(1);
    }

    public OptionalDouble returnSince(LocalDate start) {
        int base = indexOnOrBefore(start);
        if (base < 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(lastClose() / closes[base] - 1);
    }

    public OptionalDouble rateOfChange(int tradingDays) {
        if (tradingDays < 1 || closes.length <= tradingDays) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(lastClose() / closes[closes.length - 1 - tradingDays] - 1);
    }

    public OptionalDouble annualizedVolatility(int tradingDays) {
        if (tradingDays < 2 || closes.length <= tradingDays) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Indicators.last(Indicators.rollingVolatility(closes, tradingDays)));
    }

    public OptionalDouble maxDrawdownSince(LocalDate start) {
        int base = indexOnOrBefore(start);
        if (base < 0) {
            return OptionalDouble.empty();
        }
        double peak = closes[base];
        double maxDrawdown = 0;
        for (int i = base; i < closes.length; i++) {
            peak = Math.max(peak, closes[i]);
            maxDrawdown = Math.min(maxDrawdown, closes[i] / peak - 1);
        }
        return OptionalDouble.of(maxDrawdown);
    }

    public OptionalDouble rsi(int period) {
        if (period < 1 || closes.length <= period) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Indicators.last(Indicators.rsi(closes, period)));
    }

    /**
     * Series rebased to 100 at the period start. When the history begins after {@code start}, the first available
     * close is used as base and the result is flagged as partial.
     *
     * @param weekly keep only the last trading day of each ISO week (plus the base and last points)
     */
    public NormalizedSeries normalizedSince(LocalDate start, boolean weekly) {
        requireNotEmpty();
        int base = indexOnOrBefore(start);
        boolean partial = base < 0;
        int from = partial ? 0 : base;
        List<NormalizedSeries.Point> points = new ArrayList<>();
        for (int i = from; i < closes.length; i++) {
            boolean keep = !weekly || i == from || i == closes.length - 1 || !sameIsoWeek(dates[i], dates[i + 1]);
            if (keep) {
                points.add(new NormalizedSeries.Point(dates[i], 100 * closes[i] / closes[from]));
            }
        }
        return new NormalizedSeries(dates[from], partial, points);
    }

    /** Index of the last close on or before {@code date}, or -1. */
    int indexOnOrBefore(LocalDate date) {
        int index = Arrays.binarySearch(dates, date);
        return index >= 0 ? index : -index - 2;
    }

    private static boolean sameIsoWeek(LocalDate a, LocalDate b) {
        return a.get(IsoFields.WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_BASED_YEAR)
                && a.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private void requireNotEmpty() {
        if (closes.length == 0) {
            throw new IllegalStateException("price series is empty");
        }
    }
}
