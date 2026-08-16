package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.FittedParameter.Format;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.ParameterSpec;
import com.stockforecast.quant.Indicators;
import org.hipparchus.stat.regression.SimpleRegression;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordinary least-squares trend on log prices: {@code ln P(i) = intercept + slope * i} over the most recent
 * {@code lookback} closes, extrapolated as {@code F(t + k) = exp(intercept + slope * (i(t) + k))}.
 *
 * <p>Log prices make the trend a constant growth rate and keep forecasts positive (a straight line on raw prices
 * can cross zero). The fitted line's value today can differ from the last close; the forecast follows the line.
 * Regression uses Hipparchus {@link SimpleRegression}.
 */
public final class LinearRegressionForecastModel extends CalibratedPricePathModel {

    public static final String LOOKBACK = "lookback";
    /** Fewest closes a trend is fitted on, also when the configured lookback is not yet available. */
    static final int MINIMUM_FIT_OBSERVATIONS = 60;
    static final int MAXIMUM_LOOKBACK = 1260;

    private final List<ParameterSpec> parameters;

    public LinearRegressionForecastModel(EmpiricalIntervalCalibrator calibrator, int defaultLookback) {
        super(calibrator);
        this.parameters = List.of(ParameterSpec.integer(LOOKBACK, "Lookback (trading days)", defaultLookback,
                MINIMUM_FIT_OBSERVATIONS, MAXIMUM_LOOKBACK,
                "Most recent closes the trend line is fitted to (fewer when less history is available)"));
    }

    @Override
    public ForecastModelType type() {
        return ForecastModelType.LINEAR_REGRESSION;
    }

    @Override
    public List<ParameterSpec> parameters() {
        return parameters;
    }

    @Override
    public int minimumObservations(ModelParameters parameters) {
        return MINIMUM_FIT_OBSERVATIONS;
    }

    @Override
    protected Fit fit(double[] closes, ModelParameters parameters) {
        int lookback = parameters.integer(LOOKBACK);
        double[] logCloses = new double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            logCloses[i] = Math.log(closes[i]);
        }
        PointForecaster forecaster = new PointForecaster() {
            @Override
            public int firstOrigin() {
                return MINIMUM_FIT_OBSERVATIONS - 1;
            }

            @Override
            public double[] forecastFrom(int origin, int steps) {
                Trend trend = Trend.fit(logCloses, origin, lookback);
                double[] forecast = new double[steps];
                for (int k = 1; k <= steps; k++) {
                    forecast[k - 1] = Math.exp(trend.valueAt(trend.observations() - 1 + k));
                }
                return forecast;
            }
        };
        Trend trend = Trend.fit(logCloses, closes.length - 1, lookback);
        List<FittedParameter> fitted = new ArrayList<>();
        addIfFinite(fitted, "intercept", "Intercept (log price)", trend.intercept(), Format.NUMBER);
        addIfFinite(fitted, "slope", "Slope (log price per trading day)", trend.slope(), Format.NUMBER);
        addIfFinite(fitted, "rSquared", "R²", trend.rSquared(), Format.NUMBER);
        addIfFinite(fitted, "annualizedTrend", "Trend growth per year",
                Math.exp(trend.slope() * Indicators.TRADING_DAYS_PER_YEAR) - 1, Format.PERCENT);
        addIfFinite(fitted, "trendValue", "Trend line today", Math.exp(trend.valueAt(trend.observations() - 1)),
                Format.PRICE);
        addIfFinite(fitted, "observations", "Observations fitted", trend.observations(), Format.NUMBER);
        return new Fit(forecaster, "Log-linear trend over the last %d trading days".formatted(trend.observations()),
                fitted, List.of("Assumes the past trend continues at a constant growth rate. R² describes how well a "
                        + "straight line fitted the past, not how reliable the forecast is."));
    }

    /**
     * @param observations number of closes in the fitted window; the window's last close has index
     *                     {@code observations - 1}
     */
    private record Trend(double intercept, double slope, double rSquared, int observations) {

        static Trend fit(double[] logCloses, int origin, int lookback) {
            int from = Math.max(0, origin - lookback + 1);
            SimpleRegression regression = new SimpleRegression(true);
            for (int i = from; i <= origin; i++) {
                regression.addData(i - from, logCloses[i]);
            }
            return new Trend(regression.getIntercept(), regression.getSlope(), regression.getRSquare(),
                    origin - from + 1);
        }

        double valueAt(double index) {
            return intercept + slope * index;
        }
    }
}
