package com.stockforecast.forecasting.domain;

import java.util.List;
import java.util.Set;

/**
 * A forecasting model. Implementations are pure functions of their input: a forecast may only use the observations
 * in the {@link ForecastInput} it receives, which is what makes walk-forward backtests free of look-ahead.
 *
 * <p>Evaluation is deliberately not part of this contract: any model is backtested by a
 * {@link com.stockforecast.forecasting.domain.backtest.ForecastBacktester}, so every model is measured the same way.
 */
public interface StockForecastModel {

    ForecastModelType type();

    default Set<ForecastTarget> supportedTargets() {
        return Set.of(ForecastTarget.PRICE, ForecastTarget.RETURN);
    }

    /** Tunable settings; empty when the model has none. */
    default List<ParameterSpec> parameters() {
        return List.of();
    }

    /**
     * Validates {@code requested} and fills in defaults.
     *
     * @throws InvalidForecastParameterException for unknown, malformed or out-of-range settings
     */
    default ModelParameters resolveParameters(ModelParameters requested) {
        return requested.resolve(type(), parameters());
    }

    /** The fewest observations this model needs with the given (resolved) settings. */
    int minimumObservations(ModelParameters parameters);

    /**
     * @throws InsufficientForecastHistoryException if {@code input} is shorter than
     *                                              {@link #minimumObservations(ModelParameters)}
     */
    ModelForecast forecast(ForecastInput input, ForecastRequest request);
}
