package com.stockforecast.forecasting.domain.backtest;

import com.stockforecast.forecasting.domain.ForecastTarget;

import java.util.List;

/**
 * Accuracy of a model's horizon-step forecasts over a backtest, in target units.
 *
 * <ul>
 *     <li>MAE = mean |actual - predicted|; RMSE = sqrt(mean (actual - predicted)²); mean error = mean
 *     (actual - predicted), positive when the model under-predicted.</li>
 *     <li>MAPE = mean |error| / |actual| and sMAPE = mean 2|error| / (|actual| + |predicted|); price targets only,
 *     because returns close to zero make percentage errors explode.</li>
 *     <li>Direction accuracy: share of correct up/down calls among origins where the model predicted a change and
 *     the price did change. The naive model never predicts a change, so it has none.</li>
 *     <li>Up-move share: share of origins where the price rose, the score of always predicting "up".</li>
 *     <li>Interval coverage: share of outcomes inside the forecast range (nominally the confidence level).</li>
 *     <li>Skill vs naive: {@code 1 - RMSE / RMSE(naive)} on the same origins; positive means better than
 *     carrying the last close forward.</li>
 * </ul>
 * Undefined metrics are {@code null}.
 */
public record BacktestMetrics(int observations, double mae, double rmse, double meanError, Double mape, Double smape,
                              Double directionAccuracy, int directionalCalls, Double upMoveShare,
                              Double intervalCoverage, double naiveRmse, Double skillVsNaive,
                              BaselineComparison baselineComparison) {

    /** |skill| below this is reported as similar to the baseline. */
    public static final double MATERIAL_SKILL = 0.05;
    private static final double NO_CHANGE_TOLERANCE = 1e-9;

    public enum BaselineComparison {
        BETTER,
        SIMILAR,
        WORSE,
        /** The model is the baseline. */
        NOT_APPLICABLE
    }

    public static BacktestMetrics compute(List<BacktestObservation> observations, ForecastTarget target,
                                          boolean baseline) {
        int n = observations.size();
        if (n == 0) {
            throw new IllegalArgumentException("no observations to evaluate");
        }
        double absoluteSum = 0;
        double squaredSum = 0;
        double errorSum = 0;
        double percentageSum = 0;
        double symmetricSum = 0;
        double naiveSquaredSum = 0;
        int calls = 0;
        int correct = 0;
        int moves = 0;
        int upMoves = 0;
        int covered = 0;
        for (BacktestObservation observation : observations) {
            double error = observation.error();
            absoluteSum += Math.abs(error);
            squaredSum += error * error;
            errorSum += error;
            if (target == ForecastTarget.PRICE) {
                percentageSum += Math.abs(error) / Math.abs(observation.actual());
                symmetricSum += 2 * Math.abs(error) / (Math.abs(observation.actual()) + Math.abs(observation.predicted()));
            }
            double naiveError = observation.actual()
                    - target.fromPrice(observation.originPrice(), observation.originPrice());
            naiveSquaredSum += naiveError * naiveError;

            double tolerance = NO_CHANGE_TOLERANCE * observation.originPrice();
            double actualChange = observation.actualPrice() - observation.originPrice();
            double predictedChange = observation.predictedPrice() - observation.originPrice();
            if (Math.abs(actualChange) > tolerance) {
                moves++;
                if (actualChange > 0) {
                    upMoves++;
                }
                if (Math.abs(predictedChange) > tolerance) {
                    calls++;
                    if (Math.signum(actualChange) == Math.signum(predictedChange)) {
                        correct++;
                    }
                }
            }
            if (observation.withinRange()) {
                covered++;
            }
        }
        double rmse = Math.sqrt(squaredSum / n);
        double naiveRmse = Math.sqrt(naiveSquaredSum / n);
        Double skill = baseline || naiveRmse == 0 ? null : 1 - rmse / naiveRmse;
        BaselineComparison comparison = baseline ? BaselineComparison.NOT_APPLICABLE
                : skill == null ? BaselineComparison.SIMILAR
                : skill >= MATERIAL_SKILL ? BaselineComparison.BETTER
                : skill <= -MATERIAL_SKILL ? BaselineComparison.WORSE
                : BaselineComparison.SIMILAR;
        boolean percentages = target == ForecastTarget.PRICE;
        return new BacktestMetrics(n, absoluteSum / n, rmse, errorSum / n,
                percentages ? percentageSum / n : null, percentages ? symmetricSum / n : null,
                calls == 0 ? null : (double) correct / calls, calls,
                moves == 0 ? null : (double) upMoves / moves,
                (double) covered / n, naiveRmse, skill, comparison);
    }
}
