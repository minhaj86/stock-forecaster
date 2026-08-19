package com.stockforecast.forecasting.domain.feature;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * The features available on one day; features still in their warm-up period are absent.
 */
public record FeaturePoint(LocalDate date, Map<Feature, Double> values) {

    public FeaturePoint {
        values = Collections.unmodifiableMap(values.isEmpty() ? new EnumMap<>(Feature.class) : new EnumMap<>(values));
    }

    public OptionalDouble value(Feature feature) {
        Double value = values.get(feature);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /** {@code true} when every feature is defined, i.e. the row can be used for training. */
    public boolean complete() {
        return values.size() == Feature.values().length;
    }
}
