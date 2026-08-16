package com.stockforecast.forecasting.domain.backtest;

import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.StockForecastModel;

/**
 * Measures how a model would have performed historically, using only data available at each forecast origin.
 */
public interface ForecastBacktester {

    /**
     * @throws com.stockforecast.forecasting.domain.InsufficientForecastHistoryException if the history cannot
     *                                                                                   provide enough origins
     */
    BacktestResult backtest(StockForecastModel model, ForecastInput input, BacktestRequest request);
}
