package com.stockforecast.forecasting.application;

import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.IntervalCalibration;
import com.stockforecast.forecasting.domain.ParameterSpec;
import com.stockforecast.forecasting.domain.backtest.BacktestMetrics.BaselineComparison;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Forecasting read models, designed as client contracts. Values in target units are prices for {@code PRICE} and
 * decimal fractions (0.052 = 5.2%) for {@code RETURN}; all ratios are decimal fractions.
 */
public final class ForecastViews {

    public static final String DISCLAIMER = "Forecasts are based on historical data and statistical models. "
            + "They are informational only and are not financial advice.";

    private ForecastViews() {
    }

    /**
     * @param specification the fitted model, e.g. {@code Damped trend (α=0.981, β=0.012, φ=0.950)}
     * @param history       recent observations in target units ({@code RETURN}: relative to the last close)
     * @param forecast      one point per future trading day, in target units
     * @param backtest      walk-forward accuracy of this exact configuration, or {@code null} with a warning
     */
    public record ForecastResult(String symbol, String companyName, String currency, ForecastModelType model,
                                 String modelName, String modelSummary, String specification, ForecastTarget target,
                                 int horizonTradingDays, double confidenceLevel, Instant generatedAt,
                                 LocalDate dataThrough, Instant lastSyncedAt, String dataProvider,
                                 boolean syntheticData, String engine, Map<String, String> parameters,
                                 List<FittedParameter> fitted, List<SeriesPoint> history,
                                 List<ForecastPoint> forecast, ForecastSummary summary, IntervalCalibration interval,
                                 BacktestSummary backtest, List<String> notes, List<String> warnings,
                                 String disclaimer) {
    }

    public record SeriesPoint(LocalDate date, double value) {
    }

    /**
     * @param expected   central (median) estimate
     * @param lowerBound lower end of the range at {@code confidenceLevel}
     * @param upperBound upper end of the range at {@code confidenceLevel}
     */
    public record ForecastPoint(int step, LocalDate date, double expected, double lowerBound, double upperBound) {
    }

    /**
     * The forecast at the horizon, as prices and as returns relative to the last close.
     */
    public record ForecastSummary(LocalDate targetDate, double lastClose, double expectedPrice, double lowerPrice,
                                  double upperPrice, double expectedReturn, double lowerReturn, double upperReturn) {
    }

    /**
     * @param observations              evaluated forecast origins
     * @param minimumTrainingObservations history available at the earliest origin
     */
    public record BacktestSummary(LocalDate trainingStart, LocalDate evaluationStart, LocalDate evaluationEnd,
                                  int observations, int originStepDays, int minimumTrainingObservations,
                                  MetricsView metrics) {
    }

    /**
     * Errors are in target units. See {@link com.stockforecast.forecasting.domain.backtest.BacktestMetrics}.
     */
    public record MetricsView(double mae, double rmse, double meanError, Double mape, Double smape,
                              Double directionAccuracy, int directionalCalls, Double upMoveShare,
                              Double intervalCoverage, double naiveRmse, Double skillVsNaive,
                              BaselineComparison baselineComparison) {
    }

    public record BacktestView(String symbol, String currency, ForecastModelType model, String modelName,
                               ForecastTarget target, int horizonTradingDays, double confidenceLevel,
                               Map<String, String> parameters, Instant generatedAt, LocalDate dataThrough,
                               String dataProvider, boolean syntheticData, BacktestSummary summary,
                               List<ObservationView> observations, String methodology, String disclaimer) {
    }

    public record ObservationView(LocalDate originDate, LocalDate targetDate, double actual, double predicted,
                                  double lowerBound, double upperBound) {
    }

    public record ModelCatalogView(String symbol, HistoryStatus history, Defaults defaults, List<ModelInfo> models,
                                   String disclaimer) {
    }

    /**
     * @param requiredObservations trading days of history required before any model forecasts
     */
    public record HistoryStatus(String dataStatus, LocalDate dataThrough, int availableObservations,
                                int requiredObservations) {
    }

    public record Defaults(ForecastModelType model, ForecastTarget target, int horizon, int maxHorizon,
                           List<Integer> horizons, double confidenceLevel) {
    }

    /**
     * @param available            {@code false} for planned models, which are rejected when requested
     * @param minimumObservations  history required with default settings, or {@code null} when not available
     */
    public record ModelInfo(ForecastModelType model, String name, boolean available, String engine, String summary,
                            String assumptions, String limitations, Set<ForecastTarget> targets,
                            List<ParameterSpec> parameters, Integer minimumObservations) {
    }
}
