package com.stockforecast.forecasting.application;

import com.stockforecast.forecasting.domain.ForecastModelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Forecasting settings ({@code app.forecast}). Changing them changes results, so they are part of every cache key.
 *
 * @param minimumHistoryDays         trading days of history required before any forecast is produced
 * @param maxHistoryDays             most recent trading days loaded for modelling and backtesting
 * @param intervalCalibrationOrigins past origins whose errors calibrate the uncertainty range
 * @param intervalMinimumSamples     measured errors required per step before extrapolating the range
 * @param horizons                   horizons offered to clients (any value up to {@code maxHorizon} is accepted)
 */
@Validated
@ConfigurationProperties("app.forecast")
public record ForecastProperties(
        @NotNull @DefaultValue("EXPONENTIAL_SMOOTHING") ForecastModelType defaultModel,
        @Min(1) @Max(252) @DefaultValue("30") int defaultHorizon,
        @Min(1) @Max(252) @DefaultValue("90") int maxHorizon,
        @NotNull @DefaultValue({"5", "10", "20", "30", "60", "90"}) List<Integer> horizons,
        @DecimalMin("0.5") @DecimalMax("0.99") @DefaultValue("0.8") double confidenceLevel,
        @Min(30) @Max(2000) @DefaultValue("250") int minimumHistoryDays,
        @Min(250) @Max(5000) @DefaultValue("1500") int maxHistoryDays,
        @Min(20) @Max(1000) @DefaultValue("250") int intervalCalibrationOrigins,
        @Min(5) @Max(250) @DefaultValue("20") int intervalMinimumSamples,
        @NotNull @DefaultValue("12h") Duration cacheTimeToLive,
        @Valid @NotNull @DefaultValue Backtest backtest,
        @Valid @NotNull @DefaultValue MovingAverage movingAverage,
        @Valid @NotNull @DefaultValue LinearRegression linearRegression,
        @Valid @NotNull @DefaultValue ExponentialSmoothing exponentialSmoothing) {

    @AssertTrue(message = "default-horizon and every offered horizon must be between 1 and max-horizon")
    public boolean isHorizonConfigurationConsistent() {
        return defaultHorizon <= maxHorizon && horizons.stream().allMatch(h -> h != null && h >= 1 && h <= maxHorizon);
    }

    @AssertTrue(message = "minimum-history-days must not exceed max-history-days")
    public boolean isHistoryConfigurationConsistent() {
        return minimumHistoryDays <= maxHistoryDays;
    }

    /**
     * @param evaluationWindowDays trading days spanned by the backtest origins
     * @param originStepDays       trading days between backtest origins
     * @param minimumOrigins       backtests with fewer origins are not reported
     */
    public record Backtest(@Min(20) @Max(2000) @DefaultValue("250") int evaluationWindowDays,
                           @Min(1) @Max(60) @DefaultValue("5") int originStepDays,
                           @Min(5) @Max(500) @DefaultValue("20") int minimumOrigins) {
    }

    public record MovingAverage(@Min(5) @Max(200) @DefaultValue("20") int defaultWindow) {
    }

    public record LinearRegression(@Min(60) @Max(1260) @DefaultValue("252") int defaultLookback) {
    }

    /**
     * @param optimizeParameters estimate smoothing weights from the data; when {@code false} the defaults below apply
     */
    public record ExponentialSmoothing(@DefaultValue("true") boolean optimizeParameters,
                                       @Min(60) @Max(1260) @DefaultValue("504") int defaultLookback,
                                       @DecimalMin("0.0001") @DecimalMax("0.9999") @DefaultValue("0.5") double alpha,
                                       @DecimalMin("0.0001") @DecimalMax("0.9999") @DefaultValue("0.1") double beta,
                                       @DecimalMin("0.8") @DecimalMax("0.98") @DefaultValue("0.95") double phi) {
    }

    /** Short, stable digest of every setting that influences results; part of cache keys. */
    public String fingerprint() {
        String settings = String.join(",", String.valueOf(confidenceLevel), String.valueOf(minimumHistoryDays),
                String.valueOf(maxHistoryDays), String.valueOf(intervalCalibrationOrigins),
                String.valueOf(intervalMinimumSamples), String.valueOf(backtest.evaluationWindowDays()),
                String.valueOf(backtest.originStepDays()), String.valueOf(backtest.minimumOrigins()),
                String.valueOf(exponentialSmoothing.optimizeParameters()),
                String.valueOf(exponentialSmoothing.alpha()), String.valueOf(exponentialSmoothing.beta()),
                String.valueOf(exponentialSmoothing.phi()));
        return Integer.toHexString(settings.hashCode());
    }
}
