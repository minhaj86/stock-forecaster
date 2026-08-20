package com.stockforecast.forecasting.application;

import com.stockforecast.forecasting.application.ForecastViews.BacktestSummary;
import com.stockforecast.forecasting.application.ForecastViews.BacktestView;
import com.stockforecast.forecasting.application.ForecastViews.Defaults;
import com.stockforecast.forecasting.application.ForecastViews.ForecastPoint;
import com.stockforecast.forecasting.application.ForecastViews.ForecastResult;
import com.stockforecast.forecasting.application.ForecastViews.ForecastSummary;
import com.stockforecast.forecasting.application.ForecastViews.HistoryStatus;
import com.stockforecast.forecasting.application.ForecastViews.MetricsView;
import com.stockforecast.forecasting.application.ForecastViews.ModelCatalogView;
import com.stockforecast.forecasting.application.ForecastViews.ModelInfo;
import com.stockforecast.forecasting.application.ForecastViews.ObservationView;
import com.stockforecast.forecasting.application.ForecastViews.SeriesPoint;
import com.stockforecast.forecasting.domain.FittedParameter;
import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.ForecastRequest;
import com.stockforecast.forecasting.domain.ForecastTarget;
import com.stockforecast.forecasting.domain.InsufficientForecastHistoryException;
import com.stockforecast.forecasting.domain.IntervalCalibration;
import com.stockforecast.forecasting.domain.InvalidForecastHorizonException;
import com.stockforecast.forecasting.domain.ModelForecast;
import com.stockforecast.forecasting.domain.ModelParameters;
import com.stockforecast.forecasting.domain.PathPoint;
import com.stockforecast.forecasting.domain.PricePoint;
import com.stockforecast.forecasting.domain.StockForecastModel;
import com.stockforecast.forecasting.domain.TradingCalendar;
import com.stockforecast.forecasting.domain.UnsupportedForecastModelException;
import com.stockforecast.forecasting.domain.UnsupportedForecastTargetException;
import com.stockforecast.forecasting.domain.backtest.BacktestMetrics;
import com.stockforecast.forecasting.domain.backtest.BacktestRequest;
import com.stockforecast.forecasting.domain.backtest.BacktestResult;
import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.PriceHistory;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.stock.StockRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Forecast use cases: validates the request, loads stored prices, runs the model through its
 * {@link ForecastProvider}, evaluates the same configuration walk-forward and assembles a result that always carries
 * uncertainty ranges, backtest accuracy, caveats and the disclaimer.
 *
 * <p>Results and backtests are cached per stock, model configuration, settings and data version (the time of the
 * last successful market-data synchronisation), so new data makes earlier forecasts unreachable without explicit
 * eviction. All models run synchronously: the most expensive configuration (exponential smoothing with automatic
 * variant selection, including its backtest) takes well under a second, and repeated requests are served from cache.
 */
@Service
public class ForecastService {

    public static final String FORECAST_CACHE = "forecast-result";
    public static final String BACKTEST_CACHE = "forecast-backtest";

    static final long MAX_ORDINARY_GAP_DAYS = 7;
    /** Backtest coverage this far below the nominal confidence is flagged as an optimistic range. */
    static final double COVERAGE_SHORTFALL = 0.15;
    private static final int MIN_HISTORY_POINTS = 60;
    private static final int MAX_HISTORY_POINTS = 250;

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    private final List<ForecastProvider> providers;
    private final PriceHistory priceHistory;
    private final StockCatalog stockCatalog;
    private final TradingCalendar calendar;
    private final ForecastProperties properties;
    private final CacheManager cacheManager;
    private final Clock clock;

    public ForecastService(List<ForecastProvider> providers, PriceHistory priceHistory, StockCatalog stockCatalog,
                           TradingCalendar calendar, ForecastProperties properties, CacheManager cacheManager,
                           Clock clock) {
        this.providers = List.copyOf(providers);
        this.priceHistory = priceHistory;
        this.stockCatalog = stockCatalog;
        this.calendar = calendar;
        this.properties = properties;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    public ForecastResult forecast(String symbol, ForecastQuery query) {
        StockRef stock = stockCatalog.getBySymbol(symbol);
        Plan plan = plan(query);
        MarketDataStatus status = status(stock);
        Optional<String> key = cacheKey(stock, plan, status);
        Cache cache = cacheManager.getCache(FORECAST_CACHE);
        if (cache != null && key.isPresent()) {
            ForecastResult cached = cache.get(key.get(), ForecastResult.class);
            if (cached != null) {
                return cached;
            }
        }
        ForecastInput input = loadInput(stock, status, plan.requiredObservations());
        long started = System.nanoTime();
        ModelForecast forecast = plan.model().forecast(input, plan.request());
        Evaluation evaluation = evaluate(stock, plan, status, input);
        log.debug("Forecast {} {} {}d computed in {} ms", stock.symbol(), plan.type(), plan.request().horizon(),
                (System.nanoTime() - started) / 1_000_000);
        ForecastResult result = assemble(stock, status, plan, input, forecast, evaluation);
        if (cache != null && key.isPresent()) {
            cache.put(key.get(), result);
        }
        return result;
    }

    public BacktestView backtest(String symbol, ForecastQuery query) {
        StockRef stock = stockCatalog.getBySymbol(symbol);
        Plan plan = plan(query);
        MarketDataStatus status = status(stock);
        BacktestRequest request = backtestRequest(plan);
        ForecastInput input = loadInput(stock, status, request.requiredObservations(plan.requiredObservations()));
        BacktestResult result = runBacktest(stock, plan, status, input, request);
        PriceHistory.ProviderInfo provider = priceHistory.activeProvider();
        return new BacktestView(stock.symbol(), stock.currency(), plan.type(), ModelDescriptions.of(plan.type()).name(),
                result.target(), result.horizon(), result.confidence(), result.parameters().values(),
                clock.instant(), input.last().date(), status.provider(), provider.synthetic(), summary(result),
                result.observations().stream().map(observation -> new ObservationView(observation.originDate(),
                        observation.targetDate(), value(result.target(), observation.actual()),
                        value(result.target(), observation.predicted()), value(result.target(), observation.lower()),
                        value(result.target(), observation.upper()))).toList(),
                "Walk-forward validation: at each origin the model was refitted using only data up to that day and "
                        + "its %d-trading-day forecast was compared with the outcome. Past accuracy does not "
                        .formatted(result.horizon()) + "guarantee future accuracy.",
                ForecastViews.DISCLAIMER);
    }

    public ModelCatalogView models(String symbol) {
        StockRef stock = stockCatalog.getBySymbol(symbol);
        MarketDataStatus status = status(stock);
        int available = status.lastBarDate() == null ? 0 : loadBars(stock, status).size();
        List<ModelInfo> models = Arrays.stream(ForecastModelType.values()).map(this::describe).toList();
        return new ModelCatalogView(stock.symbol(),
                new HistoryStatus(status.state().name(), status.lastBarDate(), available,
                        properties.minimumHistoryDays()),
                new Defaults(properties.defaultModel(), ForecastTarget.PRICE, properties.defaultHorizon(),
                        properties.maxHorizon(), properties.horizons(), properties.confidenceLevel()),
                models, ForecastViews.DISCLAIMER);
    }

    private ModelInfo describe(ForecastModelType type) {
        ModelDescriptions.Description description = ModelDescriptions.of(type);
        Optional<ForecastProvider> provider = providers.stream()
                .filter(candidate -> candidate.supportedModels().contains(type)).findFirst();
        if (provider.isEmpty()) {
            return new ModelInfo(type, description.name(), false, null, description.summary(),
                    description.assumptions(), description.limitations(), Set.of(), List.of(), null);
        }
        StockForecastModel model = provider.get().model(type);
        ModelParameters defaults = model.resolveParameters(ModelParameters.none());
        return new ModelInfo(type, description.name(), true, provider.get().id(), description.summary(),
                description.assumptions(), description.limitations(), model.supportedTargets(), model.parameters(),
                Math.max(properties.minimumHistoryDays(), model.minimumObservations(defaults)));
    }

    private Plan plan(ForecastQuery query) {
        ForecastModelType type = query.model() != null ? query.model() : properties.defaultModel();
        ForecastTarget target = query.target() != null ? query.target() : ForecastTarget.PRICE;
        int horizon = query.horizon() != null ? query.horizon() : properties.defaultHorizon();
        if (horizon < 1 || horizon > properties.maxHorizon()) {
            throw new InvalidForecastHorizonException(horizon, properties.maxHorizon());
        }
        ForecastProvider provider = providers.stream()
                .filter(candidate -> candidate.supportedModels().contains(type)).findFirst()
                .orElseThrow(() -> new UnsupportedForecastModelException(type));
        StockForecastModel model = provider.model(type);
        if (!model.supportedTargets().contains(target)) {
            throw new UnsupportedForecastTargetException(target);
        }
        ModelParameters parameters = model.resolveParameters(ModelParameters.of(query.parameters()));
        ForecastRequest request = new ForecastRequest(target, horizon, properties.confidenceLevel(), parameters);
        int required = Math.max(properties.minimumHistoryDays(), model.minimumObservations(parameters));
        return new Plan(provider, model, request, required);
    }

    private BacktestRequest backtestRequest(Plan plan) {
        ForecastProperties.Backtest settings = properties.backtest();
        return new BacktestRequest(plan.request(), settings.evaluationWindowDays(), settings.originStepDays(),
                plan.requiredObservations(), settings.minimumOrigins());
    }

    private Evaluation evaluate(StockRef stock, Plan plan, MarketDataStatus status, ForecastInput input) {
        BacktestRequest request = backtestRequest(plan);
        try {
            return new Evaluation(runBacktest(stock, plan, status, input, request), null);
        } catch (InsufficientForecastHistoryException ex) {
            return new Evaluation(null, ("Not enough history to backtest this model at a %d-day horizon "
                    + "(%d trading days required, %d available), so its historical accuracy is unknown.")
                    .formatted(plan.request().horizon(), ex.required(), ex.available()));
        }
    }

    private BacktestResult runBacktest(StockRef stock, Plan plan, MarketDataStatus status, ForecastInput input,
                                       BacktestRequest request) {
        Optional<String> key = cacheKey(stock, plan, status);
        Cache cache = cacheManager.getCache(BACKTEST_CACHE);
        if (cache != null && key.isPresent()) {
            BacktestResult cached = cache.get(key.get(), BacktestResult.class);
            if (cached != null) {
                return cached;
            }
        }
        BacktestResult result = plan.provider().backtest(plan.type(), input, request);
        if (cache != null && key.isPresent()) {
            cache.put(key.get(), result);
        }
        return result;
    }

    private ForecastResult assemble(StockRef stock, MarketDataStatus status, Plan plan, ForecastInput input,
                                    ModelForecast forecast, Evaluation evaluation) {
        ForecastRequest request = plan.request();
        ForecastTarget target = request.target();
        PricePoint last = input.last();
        double lastClose = last.close();
        List<LocalDate> dates = calendar.nextTradingDays(last.date(), request.horizon());
        List<ForecastPoint> points = forecast.path().stream().map(point -> new ForecastPoint(point.step(),
                dates.get(point.step() - 1), value(target, target.fromPrice(point.expected(), lastClose)),
                value(target, target.fromPrice(point.lower(), lastClose)),
                value(target, target.fromPrice(point.upper(), lastClose)))).toList();
        PathPoint end = forecast.step(request.horizon());
        ForecastSummary summary = new ForecastSummary(dates.getLast(), Rounding.price(lastClose),
                Rounding.price(end.expected()), Rounding.price(end.lower()), Rounding.price(end.upper()),
                Rounding.ratio(end.expected() / lastClose - 1), Rounding.ratio(end.lower() / lastClose - 1),
                Rounding.ratio(end.upper() / lastClose - 1));
        int historyPoints = Math.clamp(3L * request.horizon(), MIN_HISTORY_POINTS, MAX_HISTORY_POINTS);
        List<SeriesPoint> history = input.tail(historyPoints).prices().stream()
                .map(point -> new SeriesPoint(point.date(), value(target, target.fromPrice(point.close(), lastClose))))
                .toList();
        List<FittedParameter> fitted = forecast.fitted().stream().map(parameter -> new FittedParameter(
                parameter.key(), parameter.label(), Rounding.significant(parameter.value()), parameter.format()))
                .toList();
        PriceHistory.ProviderInfo provider = priceHistory.activeProvider();
        ModelDescriptions.Description description = ModelDescriptions.of(plan.type());
        return new ForecastResult(stock.symbol(), stock.companyName(), stock.currency(), plan.type(),
                description.name(), description.summary(), forecast.specification(), target, request.horizon(),
                request.confidence(), clock.instant(), last.date(), status.lastSuccessAt(), status.provider(),
                provider.synthetic(), plan.provider().id(), forecast.parameters().values(), fitted, history, points,
                summary, forecast.interval(), evaluation.result() == null ? null : summary(evaluation.result()),
                forecast.notes(), warnings(status, provider, input, forecast, evaluation, request),
                ForecastViews.DISCLAIMER);
    }

    private List<String> warnings(MarketDataStatus status, PriceHistory.ProviderInfo provider, ForecastInput input,
                                  ModelForecast forecast, Evaluation evaluation, ForecastRequest request) {
        List<String> warnings = new ArrayList<>();
        if (provider.synthetic()) {
            warnings.add("Prices come from the synthetic demo provider: they are simulated, so this forecast only "
                    + "illustrates the method.");
        }
        if (status.lastError() != null && status.lastAttemptAt() != null && status.lastSuccessAt() != null
                && status.lastAttemptAt().isAfter(status.lastSuccessAt())) {
            warnings.add("The latest market-data update failed; the forecast uses data through %s."
                    .formatted(input.last().date()));
        }
        long gap = input.longestGapDays();
        if (gap > MAX_ORDINARY_GAP_DAYS) {
            warnings.add(("The price history has a gap of %d calendar days; models treat observations as "
                    + "consecutive trading days.").formatted(gap));
        }
        IntervalCalibration interval = forecast.interval();
        switch (interval.method()) {
            case EMPIRICAL_EXTRAPOLATED -> warnings.add(("The uncertainty range beyond %d trading days is "
                    + "extrapolated from shorter-horizon errors.").formatted(interval.measuredSteps()));
            case VOLATILITY_SCALED -> warnings.add("Too little history to measure this model's past errors; the "
                    + "uncertainty range is based on recent volatility.");
            case EMPIRICAL -> {
                // Fully measured: nothing to flag.
            }
        }
        if (evaluation.unavailableReason() != null) {
            warnings.add(evaluation.unavailableReason());
        } else {
            BacktestMetrics metrics = evaluation.result().metrics();
            if (metrics.baselineComparison() == BacktestMetrics.BaselineComparison.WORSE) {
                warnings.add("In the backtest this model was less accurate than simply carrying the last close "
                        + "forward (the naive baseline).");
            }
            if (metrics.intervalCoverage() != null
                    && metrics.intervalCoverage() < request.confidence() - COVERAGE_SHORTFALL) {
                warnings.add(("In the backtest only %.0f%% of outcomes fell inside the %.0f%% range; treat the range "
                        + "as optimistic.").formatted(100 * metrics.intervalCoverage(), 100 * request.confidence()));
            }
        }
        return warnings;
    }

    private static BacktestSummary summary(BacktestResult result) {
        BacktestMetrics metrics = result.metrics();
        ForecastTarget target = result.target();
        return new BacktestSummary(result.trainingStart(), result.evaluationStart(), result.evaluationEnd(),
                metrics.observations(), result.originStep(), result.minimumTraining(),
                new MetricsView(value(target, metrics.mae()), value(target, metrics.rmse()),
                        value(target, metrics.meanError()), Rounding.ratio(metrics.mape()),
                        Rounding.ratio(metrics.smape()), Rounding.ratio(metrics.directionAccuracy()),
                        metrics.directionalCalls(), Rounding.ratio(metrics.upMoveShare()),
                        Rounding.ratio(metrics.intervalCoverage()), value(target, metrics.naiveRmse()),
                        Rounding.ratio(metrics.skillVsNaive()), metrics.baselineComparison()));
    }

    /** Rounds a value in target units: prices to 4 decimals, returns to 6. */
    private static double value(ForecastTarget target, double value) {
        return target == ForecastTarget.PRICE ? Rounding.price(value) : Rounding.ratio(value);
    }

    private MarketDataStatus status(StockRef stock) {
        return priceHistory.findStatuses(List.of(stock.id())).get(stock.id());
    }

    private ForecastInput loadInput(StockRef stock, MarketDataStatus status, int required) {
        List<DailyBar> bars = status.lastBarDate() == null ? List.of() : loadBars(stock, status);
        if (bars.size() < required) {
            throw new InsufficientForecastHistoryException(required, bars.size(), status.state().name());
        }
        return ForecastInput.of(stock.symbol(), bars.stream()
                .map(bar -> new PricePoint(bar.date(), bar.adjustedClose().doubleValue(), bar.volume())).toList());
    }

    /** The most recent {@code maxHistoryDays} bars. */
    private List<DailyBar> loadBars(StockRef stock, MarketDataStatus status) {
        LocalDate to = status.lastBarDate();
        // Calendar days spanning the trading days, with slack for holidays; trimmed to the exact count below.
        LocalDate from = to.minusDays(properties.maxHistoryDays() * 7L / 5 + 30);
        List<DailyBar> bars = priceHistory.findDailyBars(stock.id(), from, to);
        int excess = bars.size() - properties.maxHistoryDays();
        return excess > 0 ? bars.subList(excess, bars.size()) : bars;
    }

    /** Empty when the stock has never been synchronised (nothing to cache against). */
    private Optional<String> cacheKey(StockRef stock, Plan plan, MarketDataStatus status) {
        if (status.lastSuccessAt() == null) {
            return Optional.empty();
        }
        ForecastRequest request = plan.request();
        return Optional.of(String.join(":", stock.id().toString(), plan.type().name(), request.target().name(),
                Integer.toString(request.horizon()), request.parameters().canonical(), properties.fingerprint(),
                Long.toString(status.lastSuccessAt().toEpochMilli())));
    }

    private record Plan(ForecastProvider provider, StockForecastModel model, ForecastRequest request,
                        int requiredObservations) {

        ForecastModelType type() {
            return model.type();
        }
    }

    /**
     * @param unavailableReason why there is no backtest, or {@code null}
     */
    private record Evaluation(BacktestResult result, String unavailableReason) {
    }
}
