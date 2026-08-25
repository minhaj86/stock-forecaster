package com.stockforecast.forecasting.api;

import com.jayway.jsonpath.JsonPath;
import com.stockforecast.stock.StockCatalog;
import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import com.stockforecast.support.WatchlistTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ForecastApiIT {

    /** Watched in every test, so the synthetic provider backfills about five years of history. */
    private static final String TRACKED = "ORCL";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;
    @Autowired
    private WatchlistTestClient watchlists;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StockCatalog stockCatalog;

    private Session session;

    @BeforeEach
    void watchTrackedStock() throws Exception {
        session = auth.registerNewUser();
        watchlists.addStock(session, watchlists.defaultWatchlistId(session), TRACKED);
        watchlists.awaitMarketData(session, TRACKED);
    }

    @Test
    void forecastsWithTheDefaultModelIncludingRangesBacktestAndDisclaimer() throws Exception {
        perform(get("/api/v1/stocks/{symbol}/forecast", TRACKED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(TRACKED))
                .andExpect(jsonPath("$.model").value("EXPONENTIAL_SMOOTHING"))
                .andExpect(jsonPath("$.target").value("PRICE"))
                .andExpect(jsonPath("$.horizonTradingDays").value(30))
                .andExpect(jsonPath("$.confidenceLevel").value(0.8))
                .andExpect(jsonPath("$.parameters.variant").value("AUTO"))
                .andExpect(jsonPath("$.specification", containsString("selected by AICc")))
                .andExpect(jsonPath("$.forecast", hasSize(30)))
                .andExpect(jsonPath("$.forecast[0].step").value(1))
                .andExpect(jsonPath("$.forecast[*].lowerBound", everyItem(greaterThan(0.0))))
                .andExpect(jsonPath("$.history", hasSize(90)))
                .andExpect(jsonPath("$.summary.expectedPrice").isNumber())
                .andExpect(jsonPath("$.summary.upperReturn").isNumber())
                .andExpect(jsonPath("$.interval.method").value("EMPIRICAL"))
                .andExpect(jsonPath("$.backtest.observations").value(50))
                .andExpect(jsonPath("$.backtest.metrics.rmse").isNumber())
                .andExpect(jsonPath("$.backtest.metrics.mape").isNumber())
                .andExpect(jsonPath("$.backtest.metrics.intervalCoverage").isNumber())
                .andExpect(jsonPath("$.backtest.metrics.baselineComparison").isString())
                .andExpect(jsonPath("$.fitted[?(@.key == 'alpha')].value").isNotEmpty())
                .andExpect(jsonPath("$.dataProvider").value("synthetic"))
                .andExpect(jsonPath("$.syntheticData").value(true))
                .andExpect(jsonPath("$.warnings[0]", containsString("synthetic")))
                .andExpect(jsonPath("$.disclaimer").value("Forecasts are based on historical data and statistical "
                        + "models. They are informational only and are not financial advice."));
    }

    @ParameterizedTest
    @CsvSource({
            "NAIVE, ''",
            "MOVING_AVERAGE, window=50",
            "LINEAR_REGRESSION, lookback=126",
            "EXPONENTIAL_SMOOTHING, variant=DAMPED",
            "EXPONENTIAL_SMOOTHING, variant=HOLT&alpha=0.9&beta=0.05"})
    void everyAvailableModelForecasts(String model, String settings) throws Exception {
        perform(get("/api/v1/stocks/{symbol}/forecast?model={model}&horizon=10&" + settings, TRACKED, model))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value(model))
                .andExpect(jsonPath("$.forecast", hasSize(10)))
                .andExpect(jsonPath("$.backtest.metrics.rmse").isNumber())
                .andExpect(jsonPath("$.modelSummary").isString());
    }

    @Test
    void returnTargetForecastsCumulativeReturns() throws Exception {
        perform(get("/api/v1/stocks/{symbol}/forecast", TRACKED).param("model", "linear-regression")
                .param("target", "return").param("horizon", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("RETURN"))
                .andExpect(jsonPath("$.history[-1:].value").value(0.0))
                .andExpect(jsonPath("$.forecast[*].expected", everyItem(lessThan(1.0))))
                .andExpect(jsonPath("$.forecast[*].lowerBound", everyItem(greaterThan(-1.0))))
                .andExpect(jsonPath("$.backtest.metrics.mape").doesNotExist());
    }

    @Test
    void invalidRequestsAreRejectedWithSpecificCodes() throws Exception {
        perform(get("/api/v1/stocks/ZZZZZ/forecast"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
        perform(get("/api/v1/stocks/{symbol}/forecast?model=MONTE_CARLO", TRACKED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORECAST_MODEL"))
                .andExpect(jsonPath("$.details.model").value("MONTE_CARLO"));
        perform(get("/api/v1/stocks/{symbol}/forecast?model=FOO", TRACKED))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        perform(get("/api/v1/stocks/{symbol}/forecast?target=DIRECTION", TRACKED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORECAST_TARGET"));
        perform(get("/api/v1/stocks/{symbol}/forecast?horizon=91", TRACKED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FORECAST_HORIZON"))
                .andExpect(jsonPath("$.details.maximum").value(90));
        perform(get("/api/v1/stocks/{symbol}/forecast?horizon=0", TRACKED))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FORECAST_HORIZON"));
        perform(get("/api/v1/stocks/{symbol}/forecast?horizon=soon", TRACKED))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        perform(get("/api/v1/stocks/{symbol}/forecast?model=MOVING_AVERAGE&window=2", TRACKED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FORECAST_PARAMETER"))
                .andExpect(jsonPath("$.details.parameter").value("window"));
        perform(get("/api/v1/stocks/{symbol}/forecast?model=NAIVE&window=20", TRACKED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'window' is not supported by the NAIVE model"));
    }

    @Test
    void untrackedStocksHaveNoHistoryToForecast() throws Exception {
        perform(get("/api/v1/stocks/CAT/forecast"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FORECAST_HISTORY"))
                .andExpect(jsonPath("$.details.requiredObservations").value(250))
                .andExpect(jsonPath("$.details.availableObservations").value(0))
                .andExpect(jsonPath("$.details.dataStatus").value("NOT_TRACKED"));
    }

    @Test
    void shortHistoryIsRefusedInsteadOfForecast() throws Exception {
        UUID stockId = stockCatalog.getBySymbol("GE").id();
        LocalDate lastDate = insertBars(stockId, 83);

        perform(get("/api/v1/stocks/GE/forecast?model=NAIVE"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FORECAST_HISTORY"))
                .andExpect(jsonPath("$.message").value("Not enough historical data is available: 250 trading days "
                        + "are required, 83 are available"))
                .andExpect(jsonPath("$.details.requiredObservations").value(250))
                .andExpect(jsonPath("$.details.availableObservations").value(83))
                .andExpect(jsonPath("$.details.dataStatus").value("READY"));
        perform(get("/api/v1/stocks/GE/forecast/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.availableObservations").value(83))
                .andExpect(jsonPath("$.history.dataThrough").value(lastDate.toString()));
    }

    @Test
    void forecastsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/{symbol}/forecast", TRACKED))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/stocks/{symbol}/forecast/models", TRACKED))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void backtestReportsEveryEvaluatedForecast() throws Exception {
        perform(get("/api/v1/stocks/{symbol}/forecast/backtest?model=MOVING_AVERAGE&horizon=10", TRACKED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("MOVING_AVERAGE"))
                .andExpect(jsonPath("$.summary.observations").value(50))
                .andExpect(jsonPath("$.summary.originStepDays").value(5))
                .andExpect(jsonPath("$.summary.minimumTrainingObservations", greaterThan(250)))
                .andExpect(jsonPath("$.summary.metrics.mae").isNumber())
                .andExpect(jsonPath("$.summary.metrics.directionAccuracy").isNumber())
                .andExpect(jsonPath("$.summary.metrics.skillVsNaive").isNumber())
                .andExpect(jsonPath("$.observations", hasSize(50)))
                .andExpect(jsonPath("$.observations[0].originDate").isString())
                .andExpect(jsonPath("$.methodology", containsString("only data up to that day")))
                .andExpect(jsonPath("$.disclaimer", containsString("not financial advice")));
    }

    @Test
    void catalogueDescribesAvailableAndPlannedModels() throws Exception {
        perform(get("/api/v1/stocks/{symbol}/forecast/models", TRACKED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models", hasSize(6)))
                .andExpect(jsonPath("$.models[?(@.model == 'EXPONENTIAL_SMOOTHING')].available").value(true))
                .andExpect(jsonPath("$.models[?(@.model == 'MONTE_CARLO')].available").value(false))
                .andExpect(jsonPath("$.models[?(@.model == 'ARIMA')].available").value(false))
                .andExpect(jsonPath("$.models[?(@.model == 'MOVING_AVERAGE')].parameters[0].name").value("window"))
                .andExpect(jsonPath("$.history.dataStatus").value("READY"))
                .andExpect(jsonPath("$.history.availableObservations", greaterThan(1000)))
                .andExpect(jsonPath("$.defaults.horizon").value(30))
                .andExpect(jsonPath("$.defaults.horizons", hasSize(6)));
    }

    /**
     * Results round-trip through Redis: the second forecast is the cached one (same generation time), and a forecast
     * reuses the backtest cached by the backtest endpoint.
     */
    @Test
    void forecastsAndBacktestsAreServedFromTheCache() throws Exception {
        String url = "/api/v1/stocks/" + TRACKED + "/forecast?model=EXPONENTIAL_SMOOTHING&variant=SIMPLE&horizon=7";
        String backtest = perform(get(url.replace("/forecast?", "/forecast/backtest?")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String first = perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.generatedAt")).isEqualTo(JsonPath.read(first, "$.generatedAt"));
        assertThat((Double) JsonPath.read(first, "$.backtest.metrics.rmse"))
                .isEqualTo(JsonPath.read(backtest, "$.summary.metrics.rmse"));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }

    /** Stores {@code count} weekday bars ending today-ish and marks the stock as synchronised. */
    private LocalDate insertBars(UUID stockId, int count) {
        List<Object[]> rows = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 5);
        LocalDate last = date;
        for (int i = 0; i < count; i++) {
            while (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
            }
            BigDecimal price = BigDecimal.valueOf(100 + i % 7);
            rows.add(new Object[]{stockId, Date.valueOf(date), price, price, price, price, price, 1000});
            last = date;
            date = date.plusDays(1);
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO market_prices (stock_id, bar_date, open, high, low, close, adjusted_close, volume,
                                           data_provider, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'synthetic', now())
                ON CONFLICT DO NOTHING""", rows);
        jdbcTemplate.update("""
                INSERT INTO market_data_sync_status (stock_id, provider, last_bar_date, last_success_at,
                                                     last_attempt_at, created_at)
                VALUES (?, 'synthetic', ?, now(), now(), now())
                ON CONFLICT (stock_id) DO NOTHING""", stockId, Date.valueOf(last));
        return last;
    }
}
