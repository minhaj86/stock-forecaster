package com.stockforecast.marketdata.infrastructure.provider.synthetic;

import com.stockforecast.marketdata.DailyBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic synthetic prices per symbol: log prices follow an Ornstein-Uhlenbeck process around a steady growth
 * trend (with rare jumps), anchored so the 2026 price level is plausible. Daily volatility matches the configured
 * annual volatility while long-run deviations from the trend stay bounded, unlike plain geometric Brownian motion.
 *
 * <p>The path always starts at {@link #EPOCH} and draws random numbers in a fixed order, so a given (symbol, date)
 * yields the same bar no matter which range is requested — incremental synchronisation therefore behaves exactly like
 * a real provider.
 */
final class SyntheticPriceModel {

    static final LocalDate EPOCH = LocalDate.of(2015, 1, 2);
    private static final LocalDate ANCHOR = LocalDate.of(2026, 1, 2);
    private static final double DT = 1.0 / 252;
    /** Mean-reversion speed per year: deviations from the trend halve in roughly nine months. */
    private static final double REVERSION = 0.9;

    private final long seed;
    private final double startLogPrice;
    private final double growth;
    private final double volatility;
    private final double baseVolume;

    SyntheticPriceModel(String symbol) {
        this.seed = symbol.hashCode() * 0x9E3779B97F4A7C15L;
        Random parameters = new Random(seed ^ 0x5DEECE66DL);
        double anchorPrice = 25 + 575 * parameters.nextDouble();
        this.growth = 0.02 + 0.13 * parameters.nextDouble();
        this.volatility = 0.16 + 0.24 * parameters.nextDouble();
        this.baseVolume = 2_000_000 + 60_000_000 * parameters.nextDouble();
        double yearsToAnchor = (ANCHOR.toEpochDay() - EPOCH.toEpochDay()) / 365.25;
        this.startLogPrice = Math.log(anchorPrice) - growth * yearsToAnchor;
    }

    List<DailyBar> bars(LocalDate from, LocalDate to) {
        List<DailyBar> bars = new ArrayList<>();
        if (to.isBefore(EPOCH) || to.isBefore(from)) {
            return bars;
        }
        Random random = new Random(seed);
        double sigmaDay = volatility * Math.sqrt(DT);
        double deviation = 0;
        double previousClose = Math.exp(startLogPrice);
        int tradingDay = 0;
        for (LocalDate date = EPOCH; !date.isAfter(to); date = date.plusDays(1)) {
            if (isWeekend(date)) {
                continue;
            }
            double shock = random.nextGaussian();
            double jump = random.nextDouble() < 0.01 ? random.nextGaussian() * 0.04 : 0;
            double gap = random.nextGaussian() * sigmaDay * 0.25;
            double wickUp = Math.abs(random.nextGaussian()) * sigmaDay * 0.5;
            double wickDown = Math.abs(random.nextGaussian()) * sigmaDay * 0.5;
            double volumeNoise = random.nextGaussian();

            deviation = deviation * (1 - REVERSION * DT) + sigmaDay * shock + jump;
            tradingDay++;
            double close = Math.exp(startLogPrice + growth * tradingDay * DT + deviation);
            double open = previousClose * Math.exp(gap);
            double high = Math.max(open, close) * (1 + wickUp);
            double low = Math.min(open, close) * (1 - wickDown);
            double intensity = 1 + Math.abs(Math.log(close / previousClose)) / sigmaDay * 0.3;
            long volume = Math.round(baseVolume * Math.exp(0.25 * volumeNoise) * intensity);

            if (!date.isBefore(from)) {
                BigDecimal closeValue = money(close);
                bars.add(new DailyBar(date, money(open), money(high), money(low), closeValue, closeValue, volume));
            }
            previousClose = close;
        }
        return bars;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
