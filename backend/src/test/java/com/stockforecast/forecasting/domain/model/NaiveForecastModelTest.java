package com.stockforecast.forecasting.domain.model;

import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.InsufficientForecastHistoryException;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.PathPoint;
import com.stockforecast.forecasting.domain.TestSeries;
import com.stockforecast.forecasting.domain.UnsupportedForecastTargetException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class NaiveForecastModelTest {

    /** Standard normal quantile for an 80% central range (scipy.stats.norm.ppf(0.9)). */
    static final double Z_80 = 1.2815515655446004;

    @Test
    void forecastsTheLastCloseWithACalibratedRange() {
        NaiveForecastModel model = new NaiveForecastModel(new EmpiricalIntervalCalibrator(100, 2));
        ForecastInput input = TestSeries.of(100, 101, 99, 102, 104, 103, 105, 107, 106, 108);

        ModelForecast forecast = model.forecast(input, request(ForecastTarget.PRICE, 2));

        assertThat(forecast.model()).isEqualTo(ForecastModelType.NAIVE);
        assertThat(forecast.path()).extracting(PathPoint::expected).containsExactly(108.0, 108.0);
        // Golden error scales from EmpiricalIntervalCalibratorTest.
        assertThat(forecast.step(1).lower()).isCloseTo(108 * Math.exp(-Z_80 * 0.018330538527467258), within(1e-9));
        assertThat(forecast.step(2).upper()).isCloseTo(108 * Math.exp(Z_80 * 0.02355963175549262), within(1e-9));
        assertThat(forecast.fitted()).singleElement().satisfies(parameter -> {
            assertThat(parameter.key()).isEqualTo("lastClose");
            assertThat(parameter.value()).isEqualTo(108);
        });
    }

    @Test
    void rangesWidenLikeTheSquareRootOfTimeForARandomWalk() {
        NaiveForecastModel model = new NaiveForecastModel(new EmpiricalIntervalCalibrator(1000, 20));
        double volatility = 0.02;
        ForecastInput input = TestSeries.of(TestSeries.randomWalk(1200, 100, 0, volatility, 42));

        ModelForecast forecast = model.forecast(input, request(ForecastTarget.PRICE, 20));

        double oneStep = Math.log(forecast.step(1).upper() / forecast.step(1).expected()) / Z_80;
        double twentySteps = Math.log(forecast.step(20).upper() / forecast.step(20).expected()) / Z_80;
        assertThat(oneStep).isCloseTo(volatility, within(volatility * 0.1));
        assertThat(twentySteps).isCloseTo(volatility * Math.sqrt(20), within(volatility * Math.sqrt(20) * 0.3));
        for (int k = 2; k <= 20; k++) {
            assertThat(forecast.step(k).upper()).isGreaterThanOrEqualTo(forecast.step(k - 1).upper());
            assertThat(forecast.step(k).lower()).isLessThanOrEqualTo(forecast.step(k - 1).lower());
        }
    }

    @Test
    void refusesToForecastWithoutEnoughHistory() {
        NaiveForecastModel model = new NaiveForecastModel(new EmpiricalIntervalCalibrator(100, 2));

        assertThatThrownBy(() -> model.forecast(TestSeries.of(100), request(ForecastTarget.PRICE, 1)))
                .isInstanceOfSatisfying(InsufficientForecastHistoryException.class, ex -> {
                    assertThat(ex.required()).isEqualTo(2);
                    assertThat(ex.available()).isEqualTo(1);
                    assertThat(ex.code()).isEqualTo("INSUFFICIENT_FORECAST_HISTORY");
                });
    }

    @Test
    void rejectsTargetsThatAreNotDerivedFromThePricePath() {
        NaiveForecastModel model = new NaiveForecastModel(new EmpiricalIntervalCalibrator(100, 2));

        assertThatThrownBy(() -> model.forecast(TestSeries.of(1, 2, 3), request(ForecastTarget.DIRECTION, 1)))
                .isInstanceOf(UnsupportedForecastTargetException.class);
    }

    @Test
    void zScoreMatchesTheNormalQuantile() {
        assertThat(CalibratedPricePathModel.zScore(0.8)).isCloseTo(Z_80, within(1e-12));
        assertThat(CalibratedPricePathModel.zScore(0.95)).isCloseTo(1.959963984540054, within(1e-12));
    }

    static ForecastRequest request(ForecastTarget target, int horizon) {
        return new ForecastRequest(target, horizon, 0.8, ModelParameters.none());
    }
}
