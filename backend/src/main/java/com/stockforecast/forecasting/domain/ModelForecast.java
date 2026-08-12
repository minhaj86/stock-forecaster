package com.stockforecast.forecasting.domain;

import java.util.List;
import java.util.Objects;

/**
 * Output of one model run: a price path with uncertainty ranges, before it is expressed in a target and dated.
 *
 * @param specification human-readable model specification, e.g. {@code SMA(20)}
 * @param parameters    the resolved settings that produced this forecast
 * @param fitted        values estimated from the data
 * @param path          one point per step, {@code path.get(k - 1)} is step {@code k}
 * @param notes         model-specific caveats worth showing to users
 */
public record ModelForecast(ForecastModelType model, String specification, ModelParameters parameters,
                            List<FittedParameter> fitted, List<PathPoint> path, IntervalCalibration interval,
                            List<String> notes) {

    public ModelForecast {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(interval, "interval");
        fitted = List.copyOf(fitted);
        path = List.copyOf(path);
        notes = List.copyOf(notes);
    }

    public PathPoint step(int step) {
        return path.get(step - 1);
    }
}
