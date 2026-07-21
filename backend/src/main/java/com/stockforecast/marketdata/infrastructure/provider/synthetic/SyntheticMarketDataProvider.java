package com.stockforecast.marketdata.infrastructure.provider.synthetic;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.application.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated end-of-day prices for development and demos without a market-data subscription.
 *
 * <p>The data is <strong>not real</strong>: the provider reports {@code synthetic=true} so every client can label
 * it. Bars for a day become available after 21:00 UTC (after the US close), like an end-of-day feed.
 */
@Component
@ConditionalOnProperty(prefix = "app.market-data", name = "provider", havingValue = SyntheticMarketDataProvider.ID)
class SyntheticMarketDataProvider implements MarketDataProvider {

    static final String ID = "synthetic";

    private static final Logger log = LoggerFactory.getLogger(SyntheticMarketDataProvider.class);
    private static final LocalTime END_OF_DAY_UTC = LocalTime.of(21, 0);
    private static final ProviderInfo INFO = new ProviderInfo(ID, "Synthetic demo data", true,
            "Simulated end-of-day prices for demonstration only. They do not reflect real market prices.");

    private final Clock clock;
    private final ConcurrentHashMap<String, SyntheticPriceModel> models = new ConcurrentHashMap<>();

    SyntheticMarketDataProvider(Clock clock) {
        this.clock = clock;
        log.warn("Market data provider 'synthetic' is active: prices are SIMULATED and must not be used for "
                + "real decisions. Configure a licensed provider for production.");
    }

    @Override
    public ProviderInfo info() {
        return INFO;
    }

    @Override
    public List<DailyBar> getHistoricalPrices(String symbol, LocalDate from, LocalDate to) {
        LocalDate latest = latestAvailableDate();
        LocalDate end = to.isAfter(latest) ? latest : to;
        return models.computeIfAbsent(symbol, SyntheticPriceModel::new).bars(from, end);
    }

    @Override
    public Optional<StockQuote> getQuote(String symbol) {
        LocalDate latest = latestAvailableDate();
        List<DailyBar> recent = getHistoricalPrices(symbol, latest.minusDays(10), latest);
        if (recent.size() < 2) {
            return Optional.empty();
        }
        DailyBar last = recent.getLast();
        DailyBar previous = recent.get(recent.size() - 2);
        BigDecimal change = last.close().subtract(previous.close());
        double changePercent = change.divide(previous.close(), 8, RoundingMode.HALF_UP).doubleValue();
        return Optional.of(new StockQuote(symbol, last.close(), change, changePercent,
                last.date().atTime(END_OF_DAY_UTC).toInstant(ZoneOffset.UTC)));
    }

    @Override
    public List<ProviderSearchResult> searchStocks(String query) {
        return List.of();
    }

    private LocalDate latestAvailableDate() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        LocalDate candidate = now.toLocalTime().isBefore(END_OF_DAY_UTC) ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }
}
