package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.ParameterSpec;
import com.stockforecast.quant.Indicators;

import java.util.Arrays;
import java.util.List;

/**
 * Projects the simple moving average of the last {@code window} closes as a flat line
 * ({@code F(t + k) = mean(P(t - window + 1) .. P(t))}). A smoothing baseline: it lags turning points and does not
 * extrapolate trends. Uses the shared {@link Indicators#sma} definition.
 */
public final class MovingAverageForecastModel extends CalibratedPricePathModel {

    public static final String WINDOW = "window";
    static final int MINIMUM_WINDOW = 5;
    static final int MAXIMUM_WINDOW = 200;

    private final List<ParameterSpec> parameters;

    public MovingAverageForecastModel(EmpiricalIntervalCalibrator calibrator, int defaultWindow) {
        super(calibrator);
        this.parameters = List.of(ParameterSpec.integer(WINDOW, "Window (trading days)", defaultWindow,
                MINIMUM_WINDOW, MAXIMUM_WINDOW, "Number of most recent closes that are averaged"));
    }

    @Override
    public ForecastModelType type() {
        return ForecastModelType.MOVING_AVERAGE;
    }

    @Override
    public List<ParameterSpec> parameters() {
        return parameters;
    }

    @Override
    public int minimumObservations(ModelParameters parameters) {
        return resolveParameters(parameters).integer(WINDOW);
    }

    @Override
    protected Fit fit(double[] closes, ModelParameters parameters) {
        int window = parameters.integer(WINDOW);
        double[] averages = Indicators.sma(closes, window);
        PointForecaster forecaster = new PointForecaster() {
            @Override
            public int firstOrigin() {
                return window - 1;
            }

            @Override
            public double[] forecastFrom(int origin, int steps) {
                double[] forecast = new double[steps];
                Arrays.fill(forecast, averages[origin]);
                return forecast;
            }
        };
        double average = Indicators.last(averages);
        double lastClose = closes[closes.length - 1];
        return new Fit(forecaster, "SMA(%d)".formatted(window),
                List.of(new FittedParameter("movingAverage", "Moving average", average, FittedParameter.Format.PRICE),
                        new FittedParameter("lastCloseVsAverage", "Last close vs average", lastClose / average - 1,
                                FittedParameter.Format.PERCENT)),
                List.of("The forecast is flat at the average of the last %d closes; it lags trends and is a smoothing "
                        .formatted(window) + "baseline rather than a trend projection."));
    }
}
