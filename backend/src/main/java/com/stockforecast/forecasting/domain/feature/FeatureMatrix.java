package com.stockforecast.forecasting.domain.feature;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Column-oriented features aligned with the observation dates; warm-up values are {@link Double#NaN}.
 */
public final class FeatureMatrix {

    private final List<LocalDate> dates;
    private final Map<Feature, double[]> columns;

    FeatureMatrix(List<LocalDate> dates, Map<Feature, double[]> columns) {
        this.dates = List.copyOf(dates);
        this.columns = new EnumMap<>(columns);
    }

    public int size() {
        return dates.size();
    }

    public List<LocalDate> dates() {
        return dates;
    }

    /** A copy of one feature's values. */
    public double[] column(Feature feature) {
        return columns.get(feature).clone();
    }

    public FeaturePoint row(int index) {
        Map<Feature, Double> values = new EnumMap<>(Feature.class);
        columns.forEach((feature, column) -> {
            if (Double.isFinite(column[index])) {
                values.put(feature, column[index]);
            }
        });
        return new FeaturePoint(dates.get(index), values);
    }
}
