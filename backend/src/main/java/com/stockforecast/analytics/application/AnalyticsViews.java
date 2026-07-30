package com.stockforecast.analytics.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Read models of the analytics use cases.
 */
public final class AnalyticsViews {

    private AnalyticsViews() {
    }

    public record PerformanceView(String period, LocalDate asOf, List<SeriesView> series) {
    }

    /**
     * @param partial history starts after the period start, so the base is the first available close
     */
    public record SeriesView(String symbol, String companyName, LocalDate baseDate, boolean partial,
                             double periodReturn, List<PointView> points) {
    }

    public record PointView(LocalDate date, double value) {
    }

    public record MoverView(String symbol, String companyName, double value) {
    }

    public record MoversView(MoverView topPerformer, MoverView weakestPerformer, MoverView highestVolatility,
                             MoverView lowestVolatility, MoverView strongestMomentum) {
    }

    /**
     * @param truncated the user watches more stocks than the dashboard includes
     */
    public record DashboardView(Instant generatedAt, String period, int watchlistCount, int stockCount,
                                boolean truncated, LocalDate dataAsOf, Instant lastSyncedAt,
                                List<String> dataProviders, List<StockSnapshot> stocks, MoversView movers,
                                PerformanceView performance) {
    }
}
