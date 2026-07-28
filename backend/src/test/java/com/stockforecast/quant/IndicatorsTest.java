package com.stockforecast.quant;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden values were computed independently with numpy (convolution SMA, {@code np.std(ddof=1)} volatility, a
 * separate Wilder RSI loop) for the closes below.
 */
class IndicatorsTest {

    private static final double TOLERANCE = 1e-9;
    private static final double[] CLOSES = {100, 102, 101, 105, 103, 108, 107, 110, 104, 106};

    @Test
    void simpleAndLogReturnsStartUndefined() {
        double[] simple = Indicators.simpleReturns(CLOSES);
        double[] log = Indicators.logReturns(CLOSES);

        assertThat(simple[0]).isNaN();
        assertThat(log[0]).isNaN();
        assertThat(simple[1]).isCloseTo(0.020000000000000018, within(TOLERANCE));
        assertThat(simple[2]).isCloseTo(-0.009803921568627416, within(TOLERANCE));
        assertThat(log[1]).isCloseTo(0.01980262729617973, within(TOLERANCE));
        assertThat(log[3]).isCloseTo(0.03883983331626396, within(TOLERANCE));
    }

    @Test
    void smaAveragesTheTrailingWindow() {
        assertDefinedFrom(Indicators.sma(CLOSES, 3), 2, 101.0, 102.66666666666667, 102.99999999999999,
                105.33333333333333, 106.0, 108.33333333333334, 107.0, 106.66666666666666);
    }

    @Test
    void emaIsSeededWithTheSmaOfTheFirstSpanValues() {
        assertDefinedFrom(Indicators.ema(CLOSES, 3), 2, 101.0, 103.0, 103.0, 105.5, 106.25, 108.125, 106.0625,
                106.03125);
        assertThat(Indicators.ema(new double[]{1, 2}, 3)).containsOnly(Double.NaN);
    }

    @Test
    void rsiFollowsWildersSmoothing() {
        assertDefinedFrom(Indicators.rsi(CLOSES, 3), 3, 85.71428571428571, 60.0, 81.17647058823529,
                70.05076142131979, 81.4756671899529, 37.99414348462664, 51.054608494654715);
        assertThat(Indicators.rsi(new double[]{5, 5, 5, 5}, 3)[3]).isEqualTo(50);
        assertThat(Indicators.rsi(new double[]{1, 2, 3, 4}, 3)[3]).isEqualTo(100);
    }

    @Test
    void rollingVolatilityIsTheAnnualisedSampleDeviationOfLogReturns() {
        assertDefinedFrom(Indicators.rollingVolatility(CLOSES, 3), 3, 0.38953258535319674, 0.4948822805999626,
                0.575496169515924, 0.5706746271111455, 0.45693120257386927, 0.6661992617988365, 0.731268824718296);
    }

    @Test
    void rollingExtremesAndRateOfChange() {
        assertDefinedFrom(Indicators.rollingMax(CLOSES, 3), 2, 102, 105, 105, 108, 108, 110, 110, 110);
        assertDefinedFrom(Indicators.rollingMin(CLOSES, 3), 2, 100, 101, 101, 103, 103, 107, 104, 104);
        assertDefinedFrom(Indicators.rateOfChange(CLOSES, 2), 2, 0.010000000000000009, 0.02941176470588225,
                0.01980198019801982, 0.02857142857142847, 0.03883495145631066, 0.0185185185185186,
                -0.028037383177570097, -0.036363636363636376);
    }

    /**
     * The defining property for modelling: every value depends only on observations up to its own index.
     */
    @Test
    void indicatorsNeverLookAhead() {
        double[] alteredFuture = CLOSES.clone();
        alteredFuture[7] = 500;
        alteredFuture[8] = 1;
        alteredFuture[9] = 999;
        int lastUnchanged = 6;

        assertSamePrefix(Indicators.sma(CLOSES, 3), Indicators.sma(alteredFuture, 3), lastUnchanged);
        assertSamePrefix(Indicators.ema(CLOSES, 3), Indicators.ema(alteredFuture, 3), lastUnchanged);
        assertSamePrefix(Indicators.rsi(CLOSES, 3), Indicators.rsi(alteredFuture, 3), lastUnchanged);
        assertSamePrefix(Indicators.rollingVolatility(CLOSES, 3), Indicators.rollingVolatility(alteredFuture, 3),
                lastUnchanged);
        assertSamePrefix(Indicators.rollingMax(CLOSES, 3), Indicators.rollingMax(alteredFuture, 3), lastUnchanged);
        assertSamePrefix(Indicators.logReturns(CLOSES), Indicators.logReturns(alteredFuture), lastUnchanged);
    }

    @Test
    void rejectsInvalidWindows() {
        assertThatThrownBy(() -> Indicators.sma(CLOSES, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Indicators.rollingVolatility(CLOSES, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Indicators.ratio(new double[2], new double[3]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertDefinedFrom(double[] series, int firstDefined, double... expected) {
        assertThat(series).hasSize(CLOSES.length);
        for (int t = 0; t < firstDefined; t++) {
            assertThat(series[t]).as("warm-up value at %d", t).isNaN();
        }
        for (int i = 0; i < expected.length; i++) {
            assertThat(series[firstDefined + i]).as("value at %d", firstDefined + i)
                    .isCloseTo(expected[i], within(TOLERANCE));
        }
    }

    private static void assertSamePrefix(double[] original, double[] altered, int lastIndex) {
        assertThat(Arrays.copyOf(altered, lastIndex + 1)).containsExactly(Arrays.copyOf(original, lastIndex + 1));
    }
}
