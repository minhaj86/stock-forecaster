package com.stockforecast.forecasting.application;

import com.stockforecast.forecasting.domain.ForecastModelType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Plain-language explanations shown next to every forecast, so users understand roughly what they are looking at and
 * where each method breaks down.
 */
public final class ModelDescriptions {

    /**
     * @param summary     what the model does, in one or two sentences
     * @param assumptions what has to hold for the forecast to be reasonable
     * @param limitations known failure modes
     */
    public record Description(String name, String summary, String assumptions, String limitations) {
    }

    private static final Map<ForecastModelType, Description> DESCRIPTIONS = new EnumMap<>(Map.of(
            ForecastModelType.NAIVE, new Description("Naive (last close)",
                    "Uses the latest closing price as the forecast for every future day. It is the baseline every "
                            + "other model has to beat.",
                    "Tomorrow's best single guess is today's price, as in a random walk.",
                    "Never predicts a change, so it offers no directional view."),
            ForecastModelType.MOVING_AVERAGE, new Description("Moving average",
                    "Projects the average of the most recent closes (for example the last 20 trading days) as a flat "
                            + "line, smoothing out day-to-day noise.",
                    "Prices fluctuate around a slowly changing level.",
                    "Lags behind turning points and cannot extrapolate a trend; it is a baseline, not a trend model."),
            ForecastModelType.LINEAR_REGRESSION, new Description("Linear regression trend",
                    "Fits a straight line through the logarithm of recent prices and extends it, i.e. assumes the "
                            + "recent average growth rate continues.",
                    "The trend of the lookback window persists over the forecast horizon.",
                    "Stock trends rarely persist and the line can start away from the last close. A high R² "
                            + "describes how well the line fitted the past, not how reliable the forecast is."),
            ForecastModelType.EXPONENTIAL_SMOOTHING, new Description("Exponential smoothing",
                    "Places more weight on recent observations while estimating the underlying level and trend of "
                            + "the time series. Variants: simple (level only), Holt (linear trend) and damped trend; "
                            + "Auto picks the variant with the lowest AICc.",
                    "The level and trend evolve gradually, so recent observations are the most informative.",
                    "Adapts quickly to recent moves, so it can extrapolate short-lived trends."),
            ForecastModelType.MONTE_CARLO, new Description("Monte Carlo simulation",
                    "Simulates thousands of possible future price paths using historical return and volatility "
                            + "estimates, and reports percentiles of the simulated outcomes.",
                    "Returns are independent and drawn from a distribution estimated from history.",
                    "Planned: not available yet."),
            ForecastModelType.ARIMA, new Description("ARIMA",
                    "Models the differenced price series with autoregressive and moving-average terms, choosing the "
                            + "order (p, d, q) by information criteria and backtest error.",
                    "After differencing, the series is stationary with linear dependence on its past.",
                    "Planned: will be served by an established statistical engine rather than an in-house "
                            + "implementation.")));

    private ModelDescriptions() {
    }

    public static Description of(ForecastModelType type) {
        return DESCRIPTIONS.get(type);
    }
}
