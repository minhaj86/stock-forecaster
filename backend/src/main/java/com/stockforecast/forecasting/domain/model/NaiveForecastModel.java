package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ModelParameters;

import java.util.Arrays;
import java.util.List;

/**
 * The baseline every other model must beat: the last observed close is the forecast for every future day
 * ({@code F(t + k) = P(t)}). For a random walk this is the optimal point forecast, so it is a demanding benchmark.
 */
public final class NaiveForecastModel extends CalibratedPricePathModel {

    public NaiveForecastModel(EmpiricalIntervalCalibrator calibrator) {
        super(calibrator);
    }

    @Override
    public ForecastModelType type() {
        return ForecastModelType.NAIVE;
    }

    @Override
    public int minimumObservations(ModelParameters parameters) {
        return 2;
    }

    @Override
    protected Fit fit(double[] closes, ModelParameters parameters) {
        PointForecaster forecaster = new PointForecaster() {
            @Override
            public int firstOrigin() {
                return 0;
            }

            @Override
            public double[] forecastFrom(int origin, int steps) {
                double[] forecast = new double[steps];
                Arrays.fill(forecast, closes[origin]);
                return forecast;
            }
        };
        return new Fit(forecaster, "Last close carried forward",
                List.of(new FittedParameter("lastClose", "Last close", closes[closes.length - 1],
                        FittedParameter.Format.PRICE)),
                List.of("The naive forecast predicts no change; it is the baseline that other models are compared "
                        + "with, and it makes no directional call."));
    }
}
