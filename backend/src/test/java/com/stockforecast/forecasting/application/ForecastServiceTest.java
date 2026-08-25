package com.stockforecast.forecasting.application;

import com.stockforecast.forecasting.application.ForecastViews.ForecastPoint;
import com.stockforecast.forecasting.application.ForecastViews.ForecastResult;
import com.stockforecast.forecasting.application.ForecastViews.ModelCatalogView;
import com.stockforecast.forecasting.application.ForecastViews.ModelInfo;
import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.InsufficientForecastHistoryException;
import com.stockforecast.forecasting.domain.InvalidForecastHorizonException;
import com.stockforecast.forecasting.domain.StockForecastModel;
import com.stockforecast.forecasting.domain.TestSeries;
import com.stockforecast.forecasting.domain.UnsupportedForecastModelException;
import com.stockforecast.forecasting.domain.UnsupportedForecastTargetException;
import com.stockforecast.forecasting.domain.WeekdayTradingCalendar;
import com.stockforecast.forecasting.domain.backtest.BacktestRequest;
import com.stockforecast.forecasting.domain.backtest.BacktestResult;
import com.stockforecast.forecasting.domain.backtest.WalkForwardBacktester;
import com.stockforecast.forecasting.domain.model.EmpiricalIntervalCalibrator;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothingForecastModel;
import com.stockforecast.forecasting.domain.model.LinearRegressionForecastModel;
import com.stockforecast.forecasting.domain.model.MovingAverageForecastModel;
import com.stockforecast.forecasting.domain.model.NaiveForecastModel;
import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForecastServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T15:00:00Z");
    private static final StockRef STOCK = new StockRef(UUID.randomUUID(), "TEST", "Test Corp", "NASDAQ", "XNAS",
            "USD", "US", "Technology", "Software", AssetType.COMMON_STOCK);

    private final PriceHistory priceHistory = mock(PriceHistory.class);
    private final StockCatalog stockCatalog = mock(StockCatalog.class);
    private final ForecastProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
            .bindOrCreate("app.forecast", ForecastProperties.class);
    private ForecastService service;

    @BeforeEach
    void setUp() {
        EmpiricalIntervalCalibrator calibrator = new EmpiricalIntervalCalibrator(250, 20);
        List<StockForecastModel> models = List.of(new NaiveForecastModel(calibrator),
                new MovingAverageForecastModel(calibrator, 20), new LinearRegressionForecastModel(calibrator, 252),
                new ExponentialSmoothingForecastModel(calibrator,
                        new ExponentialSmoothingForecastModel.Settings(504, true, 0.5, 0.1, 0.95)));
        service = new ForecastService(List.of(new InProcessProvider(models)), priceHistory, stockCatalog,
                new WeekdayTradingCalendar(), properties,
                new ConcurrentMapCacheManager(ForecastService.FORECAST_CACHE, ForecastService.BACKTEST_CACHE),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(stockCatalog.getBySymbol("TEST")).thenReturn(STOCK);
        when(priceHistory.activeProvider()).thenReturn(
                new PriceHistory.ProviderInfo("synthetic", "Synthetic", true, "Simulated prices"));
    }

    @Test
    void forecastCarriesRangesBacktestAndDisclaimer() {
        List<DailyBar> bars = givenHistory(600, NOW);

        ForecastResult result = service.forecast("TEST", query(ForecastModelType.MOVING_AVERAGE, null, null));

        LocalDate lastDate = bars.getLast().date();
        assertThat(result.horizonTradingDays()).isEqualTo(30);
        assertThat(result.dataThrough()).isEqualTo(lastDate);
        assertThat(result.forecast()).hasSize(30);
        assertThat(result.forecast().getFirst().date()).isAfter(lastDate);
        assertThat(result.forecast()).allSatisfy(point -> assertThat(point.date().getDayOfWeek().getValue())
                .isLessThanOrEqualTo(5));
        assertThat(result.forecast()).allSatisfy(point ->
                assertThat(point.lowerBound()).isLessThanOrEqualTo(point.expected())
                        .isLessThanOrEqualTo(point.upperBound()));
        ForecastPoint last = result.forecast().getLast();
        assertThat(result.summary().targetDate()).isEqualTo(last.date());
        assertThat(result.summary().expectedPrice()).isEqualTo(last.expected());
        assertThat(result.summary().lastClose())
                .isEqualTo(bars.getLast().adjustedClose().setScale(4, RoundingMode.HALF_UP).doubleValue());
        assertThat(result.history()).hasSize(90);
        assertThat(result.parameters()).containsEntry("window", "20");
        assertThat(result.backtest()).isNotNull();
        assertThat(result.backtest().observations()).isEqualTo(50);
        assertThat(result.backtest().metrics().rmse()).isPositive();
        assertThat(result.disclaimer()).isEqualTo(ForecastViews.DISCLAIMER)
                .contains("not financial advice");
        assertThat(result.syntheticData()).isTrue();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("synthetic demo provider"));
        assertThat(result.engine()).isEqualTo("java");
        assertThat(result.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void returnTargetExpressesHistoryAndForecastRelativeToTheLastClose() {
        givenHistory(600, NOW);

        ForecastResult price = service.forecast("TEST", query(ForecastModelType.NAIVE, ForecastTarget.PRICE, 10));
        ForecastResult returns = service.forecast("TEST", query(ForecastModelType.NAIVE, ForecastTarget.RETURN, 10));

        double lastClose = price.summary().lastClose();
        assertThat(returns.history().getLast().value()).isZero();
        assertThat(returns.forecast().getLast().expected()).isZero();
        assertThat(returns.forecast().getLast().upperBound())
                .isCloseTo(price.forecast().getLast().upperBound() / lastClose - 1, within(1e-5));
        assertThat(returns.summary()).isEqualTo(price.summary());
        assertThat(returns.backtest().metrics().mape()).isNull();
        assertThat(price.backtest().metrics().mape()).isNotNull();
    }

    @Test
    void resultsAreCachedPerDataVersion() {
        givenHistory(600, NOW);
        ForecastQuery query = query(ForecastModelType.NAIVE, null, 5);

        ForecastResult first = service.forecast("TEST", query);
        ForecastResult second = service.forecast("TEST", query);

        assertThat(second).isSameAs(first);
        verify(priceHistory, times(1)).findDailyBars(eq(STOCK.id()), any(), any());

        givenHistory(600, NOW.plusSeconds(3600));
        ForecastResult afterSync = service.forecast("TEST", query);

        assertThat(afterSync).isNotSameAs(first);
        verify(priceHistory, times(2)).findDailyBars(eq(STOCK.id()), any(), any());
    }

    @Test
    void refusesShortHistoryWithRequiredAndAvailableCounts() {
        givenHistory(83, NOW);

        assertThatThrownBy(() -> service.forecast("TEST", query(ForecastModelType.NAIVE, null, null)))
                .isInstanceOfSatisfying(InsufficientForecastHistoryException.class, ex -> assertThat(ex.details())
                        .containsEntry("requiredObservations", 250).containsEntry("availableObservations", 83)
                        .containsEntry("dataStatus", "READY"));
    }

    @Test
    void untrackedStocksHaveNoHistory() {
        when(priceHistory.findStatuses(anyCollection()))
                .thenReturn(Map.of(STOCK.id(), MarketDataStatus.notTracked(STOCK.id())));

        assertThatThrownBy(() -> service.forecast("TEST", query(ForecastModelType.NAIVE, null, null)))
                .isInstanceOfSatisfying(InsufficientForecastHistoryException.class, ex -> assertThat(ex.details())
                        .containsEntry("availableObservations", 0).containsEntry("dataStatus", "NOT_TRACKED"));
    }

    @Test
    void validatesModelTargetAndHorizonBeforeLoadingData() {
        assertThatThrownBy(() -> service.forecast("TEST", query(ForecastModelType.MONTE_CARLO, null, null)))
                .isInstanceOf(UnsupportedForecastModelException.class)
                .hasMessage("The MONTE_CARLO forecasting model is not available yet");
        assertThatThrownBy(() -> service.forecast("TEST",
                query(ForecastModelType.NAIVE, ForecastTarget.DIRECTION, null)))
                .isInstanceOf(UnsupportedForecastTargetException.class);
        assertThatThrownBy(() -> service.forecast("TEST", query(ForecastModelType.NAIVE, null, 91)))
                .isInstanceOf(InvalidForecastHorizonException.class)
                .hasMessage("The horizon must be between 1 and 90 trading days, but was 91");
        assertThatThrownBy(() -> service.forecast("TEST", query(ForecastModelType.NAIVE, null, 0)))
                .isInstanceOf(InvalidForecastHorizonException.class);
    }

    @Test
    void aMissingBacktestIsReportedInsteadOfHidingTheForecast() {
        givenHistory(300, NOW);

        ForecastResult result = service.forecast("TEST", query(ForecastModelType.NAIVE, null, 90));

        assertThat(result.forecast()).hasSize(90);
        assertThat(result.backtest()).isNull();
        assertThat(result.warnings()).anyMatch(warning -> warning.startsWith("Not enough history to backtest")
                && warning.contains("435 trading days required, 300 available"));
    }

    @Test
    void catalogueListsPlannedModelsAsUnavailable() {
        givenHistory(300, NOW);

        ModelCatalogView catalogue = service.models("TEST");

        assertThat(catalogue.models()).extracting(ModelInfo::model).containsExactly(ForecastModelType.values());
        assertThat(catalogue.models()).filteredOn(ModelInfo::available).extracting(ModelInfo::model)
                .containsExactly(ForecastModelType.NAIVE, ForecastModelType.MOVING_AVERAGE,
                        ForecastModelType.LINEAR_REGRESSION, ForecastModelType.EXPONENTIAL_SMOOTHING);
        assertThat(catalogue.models()).filteredOn(model -> !model.available())
                .allSatisfy(model -> assertThat(model.summary()).isNotBlank());
        assertThat(catalogue.history().availableObservations()).isEqualTo(300);
        assertThat(catalogue.history().requiredObservations()).isEqualTo(250);
        assertThat(catalogue.defaults().horizons()).containsExactly(5, 10, 20, 30, 60, 90);
    }

    private List<DailyBar> givenHistory(int size, Instant lastSuccessAt) {
        double[] closes = TestSeries.randomWalk(size, 120, 0.0003, 0.015, size);
        List<LocalDate> dates = TestSeries.weekdays(size);
        List<DailyBar> bars = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BigDecimal close = BigDecimal.valueOf(closes[i]).setScale(6, RoundingMode.HALF_UP);
            bars.add(new DailyBar(dates.get(i), close, close, close, close, close, 1_000_000));
        }
        MarketDataStatus status = new MarketDataStatus(STOCK.id(), MarketDataStatus.State.READY, "synthetic",
                dates.getLast(), lastSuccessAt, lastSuccessAt, null);
        when(priceHistory.findStatuses(anyCollection())).thenReturn(Map.of(STOCK.id(), status));
        when(priceHistory.findDailyBars(eq(STOCK.id()), any(), any())).thenReturn(bars);
        return bars;
    }

    private static ForecastQuery query(ForecastModelType model, ForecastTarget target, Integer horizon) {
        return new ForecastQuery(model, target, horizon, Map.of());
    }

    private static final class InProcessProvider implements ForecastProvider {

        private final Map<ForecastModelType, StockForecastModel> models = new EnumMap<>(ForecastModelType.class);
        private final WalkForwardBacktester backtester = new WalkForwardBacktester();

        InProcessProvider(List<StockForecastModel> models) {
            models.forEach(model -> this.models.put(model.type(), model));
        }

        @Override
        public String id() {
            return "java";
        }

        @Override
        public Set<ForecastModelType> supportedModels() {
            return models.keySet();
        }

        @Override
        public StockForecastModel model(ForecastModelType type) {
            return models.get(type);
        }

        @Override
        public BacktestResult backtest(ForecastModelType type, ForecastInput input, BacktestRequest request) {
            return backtester.backtest(models.get(type), input, request);
        }
    }
}
