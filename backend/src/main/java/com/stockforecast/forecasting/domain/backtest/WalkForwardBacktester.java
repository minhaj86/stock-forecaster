package com.stockforecast.forecasting.domain.backtest;

import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.InsufficientForecastHistoryException;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.PathPoint;
import com.stockforecast.forecasting.domain.StockForecastModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Expanding-window walk-forward validation. Origins are spaced {@code originStep} trading days apart over the last
 * {@code evaluationWindow} days whose outcome is known; at each origin the model is refitted on
 * {@link ForecastInput#truncate(int) the history up to and including that day only} and its forecast
 * {@code horizon} trading days ahead is compared with what happened. Models that use a lookback fit on the most
 * recent part of that history, so for them the window effectively rolls.
 *
 * <p>Look-ahead is prevented structurally: the model never receives a later observation, so parameter estimation,
 * interval calibration and any feature computation inside the model can only use the past. There is no random
 * train/test split.
 */
public final class WalkForwardBacktester implements ForecastBacktester {

    @Override
    public BacktestResult backtest(StockForecastModel model, ForecastInput input, BacktestRequest request) {
        ForecastRequest forecastRequest = request.forecast();
        ModelParameters parameters = model.resolveParameters(forecastRequest.parameters());
        ForecastRequest resolved = forecastRequest.withParameters(parameters);
        int horizon = resolved.horizon();
        int minimumTraining = Math.max(request.minimumTrainingObservations(),
                model.minimumObservations(parameters));
        List<Integer> origins = origins(input.size(), horizon, minimumTraining, request);
        if (origins.size() < request.minimumOrigins()) {
            throw new InsufficientForecastHistoryException(request.requiredObservations(minimumTraining),
                    input.size());
        }
        ForecastTarget target = resolved.target();
        List<BacktestObservation> observations = new ArrayList<>(origins.size());
        for (int origin : origins) {
            ForecastInput history = input.truncate(origin + 1);
            ModelForecast forecast = model.forecast(history, resolved);
            PathPoint point = forecast.step(horizon);
            double originPrice = input.close(origin);
            double actualPrice = input.close(origin + horizon);
            observations.add(new BacktestObservation(input.date(origin), input.date(origin + horizon), originPrice,
                    actualPrice, point.expected(), target.fromPrice(actualPrice, originPrice),
                    target.fromPrice(point.expected(), originPrice), target.fromPrice(point.lower(), originPrice),
                    target.fromPrice(point.upper(), originPrice)));
        }
        BacktestMetrics metrics = BacktestMetrics.compute(observations, target,
                model.type() == ForecastModelType.NAIVE);
        int firstOrigin = origins.getFirst();
        return new BacktestResult(model.type(), target, horizon, resolved.confidence(), parameters, input.date(0),
                input.date(firstOrigin), input.date(origins.getLast() + horizon), firstOrigin + 1,
                request.originStep(), metrics, observations);
    }

    /** Origin indices in chronological order; the latest is the last one whose outcome is known. */
    static List<Integer> origins(int size, int horizon, int minimumTraining, BacktestRequest request) {
        int latest = size - 1 - horizon;
        int earliest = Math.max(minimumTraining - 1, latest - request.evaluationWindow() + 1);
        List<Integer> origins = new ArrayList<>();
        for (int origin = latest; origin >= earliest; origin -= request.originStep()) {
            origins.addFirst(origin);
        }
        return origins;
    }
}
