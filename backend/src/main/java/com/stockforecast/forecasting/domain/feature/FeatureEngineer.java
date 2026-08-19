package com.stockforecast.forecasting.domain.feature;

import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.quant.Indicators;

import java.util.EnumMap;
import java.util.Map;

/**
 * Computes {@link Feature}s for a history. Every column is a trailing calculation, so the features of day {@code t}
 * are identical whether they are computed on the full history or on the history truncated at {@code t}: there is no
 * normalisation or scaling across the whole dataset here. Learned models that need scaling must fit it on their
 * training window only.
 */
public final class FeatureEngineer {

    public FeatureMatrix compute(ForecastInput input) {
        double[] closes = input.closes();
        double[] volumes = input.volumes();
        Map<Feature, double[]> columns = new EnumMap<>(Feature.class);
        columns.put(Feature.DAILY_RETURN, Indicators.simpleReturns(closes));
        columns.put(Feature.LOG_RETURN, Indicators.logReturns(closes));
        columns.put(Feature.SMA_5, Indicators.sma(closes, 5));
        columns.put(Feature.SMA_10, Indicators.sma(closes, 10));
        double[] sma20 = Indicators.sma(closes, 20);
        double[] sma50 = Indicators.sma(closes, 50);
        columns.put(Feature.SMA_20, sma20);
        columns.put(Feature.SMA_50, sma50);
        columns.put(Feature.SMA_200, Indicators.sma(closes, 200));
        columns.put(Feature.EMA_12, Indicators.ema(closes, 12));
        columns.put(Feature.EMA_26, Indicators.ema(closes, 26));
        columns.put(Feature.RSI_14, Indicators.rsi(closes, 14));
        columns.put(Feature.MOMENTUM_20, Indicators.rateOfChange(closes, 20));
        columns.put(Feature.VOLATILITY_20, Indicators.rollingVolatility(closes, 20));
        columns.put(Feature.VOLUME_CHANGE, volumeChange(volumes));
        columns.put(Feature.PRICE_TO_SMA_20, Indicators.ratio(closes, sma20));
        columns.put(Feature.SMA_20_TO_SMA_50, Indicators.ratio(sma20, sma50));
        columns.put(Feature.ROLLING_HIGH_20, Indicators.rollingMax(closes, 20));
        columns.put(Feature.ROLLING_LOW_20, Indicators.rollingMin(closes, 20));
        return new FeatureMatrix(input.dates(), columns);
    }

    /** Undefined after a day without volume (the ratio would be infinite). */
    private static double[] volumeChange(double[] volumes) {
        double[] change = new double[volumes.length];
        change[0] = Double.NaN;
        for (int t = 1; t < volumes.length; t++) {
            change[t] = volumes[t - 1] > 0 ? volumes[t] / volumes[t - 1] - 1 : Double.NaN;
        }
        return change;
    }
}
