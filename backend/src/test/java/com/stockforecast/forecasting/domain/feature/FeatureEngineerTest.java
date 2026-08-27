package com.stockforecast.forecasting.domain.feature;

import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.TestSeries;
import com.stockforecast.quant.Indicators;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FeatureEngineerTest {

    private final FeatureEngineer engineer = new FeatureEngineer();
    private final ForecastInput input = TestSeries.of(TestSeries.randomWalk(260, 100, 0.0002, 0.015, 4));

    /**
     * Features of day t must be identical whether computed on the full history or on the history truncated at t;
     * otherwise a model trained on them would see the future.
     */
    @Test
    void featuresNeverUseLaterObservations() {
        FeatureMatrix full = engineer.compute(input);

        for (int t : new int[]{0, 1, 13, 14, 19, 49, 199, 230, 259}) {
            FeatureMatrix truncated = engineer.compute(input.truncate(t + 1));
            assertThat(truncated.row(t)).as("features of day %d", t).isEqualTo(full.row(t));
        }
    }

    @Test
    void featuresReuseTheSharedIndicatorDefinitions() {
        FeatureMatrix features = engineer.compute(input);
        double[] closes = input.closes();
        int last = closes.length - 1;
        FeaturePoint row = features.row(last);

        assertThat(row.value(Feature.RSI_14)).hasValue(Indicators.rsi(closes, 14)[last]);
        assertThat(row.value(Feature.VOLATILITY_20)).hasValue(Indicators.rollingVolatility(closes, 20)[last]);
        double mean20 = 0;
        for (int i = last - 19; i <= last; i++) {
            mean20 += closes[i] / 20;
        }
        assertThat(row.value(Feature.SMA_20).orElseThrow()).isCloseTo(mean20, within(1e-9));
        assertThat(row.value(Feature.PRICE_TO_SMA_20).orElseThrow()).isCloseTo(closes[last] / mean20, within(1e-9));
        assertThat(row.value(Feature.VOLUME_CHANGE).orElseThrow())
                .isCloseTo((double) input.last().volume() / input.point(last - 1).volume() - 1, within(1e-12));
    }

    @Test
    void warmUpValuesAreAbsentUntilEnoughHistoryExists() {
        FeatureMatrix features = engineer.compute(input);

        assertThat(features.row(0).values()).doesNotContainKeys(Feature.DAILY_RETURN, Feature.SMA_5);
        assertThat(features.row(198).value(Feature.SMA_200)).isEmpty();
        assertThat(features.row(199).complete()).isTrue();
        assertThat(features.column(Feature.SMA_200)).hasSize(260);
    }
}
