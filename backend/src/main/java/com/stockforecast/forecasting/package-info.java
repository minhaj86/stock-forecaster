/**
 * Forecasting: statistical price forecasts from stored market data, with walk-forward backtesting so every model is
 * judged against a naive baseline on data it has not seen.
 *
 * <p>Forecasts are model-based estimates with uncertainty ranges, never predictions of what will happen. Models are
 * pure domain classes behind {@link com.stockforecast.forecasting.domain.StockForecastModel}; the application layer
 * reaches them through the {@code ForecastProvider} port so external engines (e.g. a Python ML service) can be added
 * without touching the API.
 */
@ApplicationModule(displayName = "Forecasting")
package com.stockforecast.forecasting;

import org.springframework.modulith.ApplicationModule;
