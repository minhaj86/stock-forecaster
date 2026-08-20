package com.stockforecast.forecasting.application;

import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.StockForecastModel;
import com.stockforecast.forecasting.domain.backtest.BacktestRequest;
import com.stockforecast.forecasting.domain.backtest.BacktestResult;

import java.util.Set;

/**
 * Port to a forecasting engine. The in-process Java models are one provider; a future Python service (gradient
 * boosting, LSTM, transformers, ARIMA/GARCH from established libraries) would be another, reached over REST or gRPC,
 * returning the same domain types so nothing above this port changes.
 */
public interface ForecastProvider {

    /** Identifier reported to clients, e.g. {@code java}. */
    String id();

    Set<ForecastModelType> supportedModels();

    /**
     * The model's contract (parameters, minimum history, forecasting). A remote provider returns a proxy.
     *
     * @throws IllegalArgumentException if the model is not in {@link #supportedModels()}
     */
    StockForecastModel model(ForecastModelType type);

    /**
     * Walk-forward evaluation of {@code type}. Remote providers can evaluate next to the model instead of being called
     * once per origin.
     */
    BacktestResult backtest(ForecastModelType type, ForecastInput input, BacktestRequest request);
}
