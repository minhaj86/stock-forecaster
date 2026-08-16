package com.stockforecast.forecasting.domain.backtest;

import com.stockforecast.forecasting.domain.ForecastRequest;

import java.util.Objects;

/**
 * @param forecast                    the forecast being evaluated (target, horizon, confidence, model settings)
 * @param evaluationWindow            trading days spanned by the forecast origins, ending at the latest origin whose
 *                                    outcome is known
 * @param originStep                  trading days between consecutive origins
 * @param minimumTrainingObservations history required at the earliest origin
 * @param minimumOrigins              fewer evaluated origins than this make the metrics meaningless
 */
public record BacktestRequest(ForecastRequest forecast, int evaluationWindow, int originStep,
                              int minimumTrainingObservations, int minimumOrigins) {

    public BacktestRequest {
        Objects.requireNonNull(forecast, "forecast");
        if (evaluationWindow < 1 || originStep < 1 || minimumTrainingObservations < 2 || minimumOrigins < 1) {
            throw new IllegalArgumentException("invalid backtest configuration");
        }
    }

    /** History needed for {@code minimumOrigins} origins with the given minimum training size. */
    public int requiredObservations(int minimumTraining) {
        return minimumTraining + forecast.horizon() + (minimumOrigins - 1) * originStep;
    }
}
