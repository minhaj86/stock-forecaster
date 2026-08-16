package com.stockforecast.forecasting.domain.backtest;

import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.ModelParameters;

import java.time.LocalDate;
import java.util.List;

/**
 * Walk-forward evaluation of one model configuration.
 *
 * @param evaluationStart  the earliest forecast origin
 * @param evaluationEnd    the latest outcome date
 * @param trainingStart    the first observation available to the earliest origin
 * @param minimumTraining  observations available at the earliest origin
 */
public record BacktestResult(ForecastModelType model, ForecastTarget target, int horizon, double confidence,
                             ModelParameters parameters, LocalDate trainingStart, LocalDate evaluationStart,
                             LocalDate evaluationEnd, int minimumTraining, int originStep, BacktestMetrics metrics,
                             List<BacktestObservation> observations) {

    public BacktestResult {
        observations = List.copyOf(observations);
    }
}
