package com.stockforecast.forecasting.api;

import com.stockforecast.forecasting.application.ForecastQuery;
import com.stockforecast.forecasting.application.ForecastService;
import com.stockforecast.forecasting.application.ForecastViews.BacktestView;
import com.stockforecast.forecasting.application.ForecastViews.ForecastResult;
import com.stockforecast.forecasting.application.ForecastViews.ModelCatalogView;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothingForecastModel;
import com.stockforecast.forecasting.domain.model.LinearRegressionForecastModel;
import com.stockforecast.forecasting.domain.model.MovingAverageForecastModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Statistical forecasts of stored prices. Every response carries uncertainty ranges, walk-forward backtest accuracy
 * and the disclaimer; the application read models are the client contract and are returned as-is.
 */
@Tag(name = "Forecasting", description = "Model-based price and return forecasts with walk-forward backtests")
@RestController
@RequestMapping("/api/v1/stocks/{symbol}/forecast")
class ForecastController {

    private final ForecastService forecastService;

    ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @Operation(summary = "Forecast a stock with one model",
            description = "Central estimate and uncertainty range for each of the next `horizon` trading days, the "
                    + "backtest accuracy of the same configuration, fitted parameters and caveats. Returns 422 "
                    + "INSUFFICIENT_FORECAST_HISTORY (with required/available observations) instead of a forecast "
                    + "from too little data.")
    @GetMapping
    ForecastResult forecast(@PathVariable String symbol,
                            @Parameter(description = "Defaults to the configured model")
                            @RequestParam(required = false) ForecastModelType model,
                            @Parameter(description = "PRICE (default) or RETURN")
                            @RequestParam(required = false) ForecastTarget target,
                            @Parameter(description = "Trading days ahead, 1 to the configured maximum")
                            @RequestParam(required = false) Integer horizon,
                            @ParameterObject ModelSettingsParameters settings) {
        return forecastService.forecast(symbol, new ForecastQuery(model, target, horizon, settings.toMap()));
    }

    @Operation(summary = "Walk-forward backtest of one model configuration",
            description = "Refits the model at each origin using only data up to that day and compares its "
                    + "horizon-step forecast with the outcome: MAE, RMSE, MAPE, sMAPE, direction accuracy, range "
                    + "coverage and skill versus the naive baseline, plus every evaluated forecast.")
    @GetMapping("/backtest")
    BacktestView backtest(@PathVariable String symbol,
                          @RequestParam(required = false) ForecastModelType model,
                          @RequestParam(required = false) ForecastTarget target,
                          @RequestParam(required = false) Integer horizon,
                          @ParameterObject ModelSettingsParameters settings) {
        return forecastService.backtest(symbol, new ForecastQuery(model, target, horizon, settings.toMap()));
    }

    @Operation(summary = "Forecasting models, their settings and this stock's history status",
            description = "Lists every model with a plain-language explanation; planned models are listed as "
                    + "unavailable.")
    @GetMapping("/models")
    ModelCatalogView models(@PathVariable String symbol) {
        return forecastService.models(symbol);
    }

    /**
     * Model-specific settings, bound from query parameters; each model rejects settings it does not use.
     *
     * @param window   moving-average window (MOVING_AVERAGE)
     * @param lookback trading days fitted (LINEAR_REGRESSION, EXPONENTIAL_SMOOTHING)
     * @param variant  AUTO, SIMPLE, HOLT or DAMPED (EXPONENTIAL_SMOOTHING)
     * @param alpha    fixed level smoothing weight (EXPONENTIAL_SMOOTHING with an explicit variant)
     * @param beta     fixed trend smoothing weight (HOLT, DAMPED)
     * @param phi      fixed trend damping (DAMPED)
     */
    record ModelSettingsParameters(String window, String lookback, String variant, String alpha, String beta,
                                   String phi) {

        Map<String, String> toMap() {
            Map<String, String> values = new LinkedHashMap<>();
            put(values, MovingAverageForecastModel.WINDOW, window);
            put(values, LinearRegressionForecastModel.LOOKBACK, lookback);
            put(values, ExponentialSmoothingForecastModel.VARIANT, variant);
            put(values, ExponentialSmoothingForecastModel.ALPHA, alpha);
            put(values, ExponentialSmoothingForecastModel.BETA, beta);
            put(values, ExponentialSmoothingForecastModel.PHI, phi);
            return values;
        }

        private static void put(Map<String, String> values, String name, String value) {
            if (value != null && !value.isBlank()) {
                values.put(name, value);
            }
        }
    }
}
