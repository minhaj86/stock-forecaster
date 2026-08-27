package com.stockforecast.forecasting.domain.backtest;

import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.InsufficientForecastHistoryException;
import com.stockforecast.forecasting.domain.IntervalCalibration;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.PathPoint;
import com.stockforecast.forecasting.domain.StockForecastModel;
import com.stockforecast.forecasting.domain.TestSeries;
import com.stockforecast.forecasting.domain.model.EmpiricalIntervalCalibrator;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothingForecastModel;
import com.stockforecast.forecasting.domain.model.LinearRegressionForecastModel;
import com.stockforecast.forecasting.domain.model.MovingAverageForecastModel;
import com.stockforecast.forecasting.domain.model.NaiveForecastModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalkForwardBacktesterTest {

    private static final EmpiricalIntervalCalibrator CALIBRATOR = new EmpiricalIntervalCalibrator(250, 20);
    private final WalkForwardBacktester backtester = new WalkForwardBacktester();

    @Test
    void originsAreSpacedOverTheEvaluationWindowEndingAtTheLastKnownOutcome() {
        BacktestRequest request = request(ForecastTarget.PRICE, 5, 100, 10, 250, 5);

        List<Integer> origins = WalkForwardBacktester.origins(400, 5, 250, request);

        assertThat(origins).containsExactly(304, 314, 324, 334, 344, 354, 364, 374, 384, 394);
    }

    @Test
    void theEarliestOriginHasTheMinimumTrainingHistory() {
        BacktestRequest request = request(ForecastTarget.PRICE, 5, 1000, 10, 250, 5);

        assertThat(WalkForwardBacktester.origins(400, 5, 250, request).getFirst()).isEqualTo(254);
    }

    /**
     * The defining property of a valid backtest: the model never sees an observation after the forecast origin.
     */
    @Test
    void theModelOnlyEverReceivesDataUpToTheForecastOrigin() {
        ForecastInput input = TestSeries.of(TestSeries.randomWalk(400, 100, 0, 0.01, 5));
        RecordingModel spy = new RecordingModel();

        BacktestResult result = backtester.backtest(spy, input, request(ForecastTarget.PRICE, 5, 100, 10, 250, 5));

        assertThat(spy.trainingEnds).hasSize(result.observations().size()).hasSize(10);
        for (int i = 0; i < spy.trainingEnds.size(); i++) {
            BacktestObservation observation = result.observations().get(i);
            assertThat(spy.trainingEnds.get(i)).isEqualTo(observation.originDate())
                    .isBefore(observation.targetDate());
            assertThat(spy.trainingSizes.get(i)).isEqualTo(304 + 10 * i + 1);
        }
        assertThat(result.minimumTraining()).isEqualTo(305);
        assertThat(result.evaluationStart()).isEqualTo(input.date(304));
        assertThat(result.evaluationEnd()).isEqualTo(input.date(399));
    }

    /**
     * Changing the future must not change any forecast made before it: proof that nothing (parameter estimation,
     * interval calibration, features) leaks from later observations.
     */
    @ParameterizedTest
    @MethodSource("realModels")
    void forecastsAreUnaffectedByLaterObservations(StockForecastModel model) {
        double[] original = TestSeries.randomWalk(420, 100, 0.0003, 0.012, 21);
        double[] alteredFuture = original.clone();
        int firstAltered = 380;
        for (int i = firstAltered; i < alteredFuture.length; i++) {
            alteredFuture[i] = original[i] * (1 + 0.5 * Math.sin(i));
        }
        BacktestRequest request = request(ForecastTarget.PRICE, 10, 150, 10, 250, 5);

        List<BacktestObservation> before = backtester.backtest(model, TestSeries.of(original), request).observations();
        List<BacktestObservation> after = backtester.backtest(model, TestSeries.of(alteredFuture), request)
                .observations();

        assertThat(before).hasSameSizeAs(after);
        int compared = 0;
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i).originDate().isBefore(TestSeries.weekdays(firstAltered).getLast().plusDays(1))) {
                assertThat(after.get(i).predicted()).isEqualTo(before.get(i).predicted());
                assertThat(after.get(i).lower()).isEqualTo(before.get(i).lower());
                assertThat(after.get(i).upper()).isEqualTo(before.get(i).upper());
                compared++;
            }
        }
        assertThat(compared).isGreaterThan(5);
    }

    static Stream<StockForecastModel> realModels() {
        return Stream.of(new NaiveForecastModel(CALIBRATOR), new MovingAverageForecastModel(CALIBRATOR, 20),
                new LinearRegressionForecastModel(CALIBRATOR, 252),
                new ExponentialSmoothingForecastModel(CALIBRATOR,
                        new ExponentialSmoothingForecastModel.Settings(504, true, 0.5, 0.1, 0.95)));
    }

    @Test
    void naiveIsTheBaselineAndMakesNoDirectionalCalls() {
        ForecastInput input = TestSeries.of(TestSeries.randomWalk(400, 100, 0, 0.01, 8));

        BacktestResult result = backtester.backtest(new NaiveForecastModel(CALIBRATOR), input,
                request(ForecastTarget.PRICE, 5, 100, 5, 250, 5));

        assertThat(result.model()).isEqualTo(ForecastModelType.NAIVE);
        assertThat(result.metrics().directionAccuracy()).isNull();
        assertThat(result.metrics().directionalCalls()).isZero();
        assertThat(result.metrics().rmse()).isEqualTo(result.metrics().naiveRmse());
        assertThat(result.metrics().baselineComparison()).isEqualTo(BacktestMetrics.BaselineComparison.NOT_APPLICABLE);
    }

    @Test
    void returnTargetsAreEvaluatedAsReturnsFromTheOrigin() {
        ForecastInput input = TestSeries.of(TestSeries.randomWalk(400, 100, 0, 0.01, 9));

        BacktestResult result = backtester.backtest(new MovingAverageForecastModel(CALIBRATOR, 20), input,
                request(ForecastTarget.RETURN, 5, 100, 5, 250, 5));

        BacktestObservation first = result.observations().getFirst();
        assertThat(first.actual()).isEqualTo(first.actualPrice() / first.originPrice() - 1);
        assertThat(first.predicted()).isEqualTo(first.predictedPrice() / first.originPrice() - 1);
        assertThat(result.metrics().mape()).isNull();
        assertThat(result.metrics().intervalCoverage()).isBetween(0.0, 1.0);
    }

    @Test
    void refusesToBacktestWithTooFewOrigins() {
        ForecastInput input = TestSeries.of(TestSeries.randomWalk(300, 100, 0, 0.01, 1));

        assertThatThrownBy(() -> backtester.backtest(new NaiveForecastModel(CALIBRATOR), input,
                request(ForecastTarget.PRICE, 30, 250, 5, 250, 20)))
                .isInstanceOfSatisfying(InsufficientForecastHistoryException.class, ex -> {
                    assertThat(ex.required()).isEqualTo(250 + 30 + 19 * 5);
                    assertThat(ex.available()).isEqualTo(300);
                });
    }

    private static BacktestRequest request(ForecastTarget target, int horizon, int window, int step,
                                           int minimumTraining, int minimumOrigins) {
        return new BacktestRequest(new ForecastRequest(target, horizon, 0.8, ModelParameters.none()), window, step,
                minimumTraining, minimumOrigins);
    }

    /** Records what each training history looked like and forecasts the last close. */
    private static final class RecordingModel implements StockForecastModel {

        private final List<LocalDate> trainingEnds = new ArrayList<>();
        private final List<Integer> trainingSizes = new ArrayList<>();

        @Override
        public ForecastModelType type() {
            return ForecastModelType.MOVING_AVERAGE;
        }

        @Override
        public int minimumObservations(ModelParameters parameters) {
            return 1;
        }

        @Override
        public ModelForecast forecast(ForecastInput input, ForecastRequest request) {
            trainingEnds.add(input.last().date());
            trainingSizes.add(input.size());
            double last = input.last().close();
            List<PathPoint> path = IntStream.rangeClosed(1, request.horizon())
                    .mapToObj(step -> new PathPoint(step, last, last * 0.9, last * 1.1)).toList();
            return new ModelForecast(type(), "spy", request.parameters(), List.of(), path,
                    new IntervalCalibration(IntervalCalibration.Method.EMPIRICAL, 0, request.horizon()), List.of());
        }
    }
}
