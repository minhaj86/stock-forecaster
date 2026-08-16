package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.InsufficientForecastHistoryException;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.PathPoint;
import com.stockforecast.forecasting.domain.StockForecastModel;
import com.stockforecast.forecasting.domain.UnsupportedForecastTargetException;
import org.hipparchus.distribution.continuous.NormalDistribution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Template for models that project a central price path: subclasses fit the model, this class adds uncertainty
 * ranges calibrated on the model's own out-of-sample errors (see {@link EmpiricalIntervalCalibrator}).
 */
public abstract class CalibratedPricePathModel implements StockForecastModel {

    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution();

    private final EmpiricalIntervalCalibrator calibrator;

    protected CalibratedPricePathModel(EmpiricalIntervalCalibrator calibrator) {
        this.calibrator = Objects.requireNonNull(calibrator, "calibrator");
    }

    @Override
    public final ModelForecast forecast(ForecastInput input, ForecastRequest request) {
        if (!supportedTargets().contains(request.target())) {
            throw new UnsupportedForecastTargetException(request.target());
        }
        ModelParameters parameters = resolveParameters(request.parameters());
        int required = minimumObservations(parameters);
        if (input.size() < required) {
            throw new InsufficientForecastHistoryException(required, input.size());
        }
        double[] closes = input.closes();
        Fit fit = fit(closes, parameters);
        int horizon = request.horizon();
        double[] central = fit.forecaster().forecastFrom(closes.length - 1, horizon);
        EmpiricalIntervalCalibrator.Calibration calibration = calibrator.calibrate(fit.forecaster(), closes, horizon);
        double z = zScore(request.confidence());
        List<PathPoint> path = new ArrayList<>(horizon);
        for (int k = 1; k <= horizon; k++) {
            double spread = z * calibration.scale(k);
            double expected = central[k - 1];
            path.add(new PathPoint(k, expected, expected * Math.exp(-spread), expected * Math.exp(spread)));
        }
        return new ModelForecast(type(), fit.specification(), parameters, fit.fitted(), path, calibration.summary(),
                fit.notes());
    }

    /**
     * Fits the model to the whole training series.
     *
     * @param closes adjusted closes, oldest first; at least {@link #minimumObservations} of them
     */
    protected abstract Fit fit(double[] closes, ModelParameters parameters);

    /** The two-sided standard normal quantile for a central range, e.g. 1.2816 for 80%. */
    static double zScore(double confidence) {
        return STANDARD_NORMAL.inverseCumulativeProbability(0.5 + confidence / 2);
    }

    /** Adds a fitted value unless it is undefined (e.g. R² of a constant series). */
    static void addIfFinite(List<FittedParameter> fitted, String key, String label, double value,
                            FittedParameter.Format format) {
        if (Double.isFinite(value)) {
            fitted.add(new FittedParameter(key, label, value, format));
        }
    }

    /**
     * @param specification human-readable description of the fitted model
     */
    protected record Fit(PointForecaster forecaster, String specification, List<FittedParameter> fitted,
                         List<String> notes) {
    }
}
