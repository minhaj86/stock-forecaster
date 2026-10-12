package com.stockforecast.marketdata.infrastructure.provider.synthetic;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderCapabilities;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.ProviderInstrumentMatch;
import com.stockforecast.marketdata.ProviderStockMetadata;
import com.stockforecast.marketdata.application.MarketDataProvider;
import com.stockforecast.marketdata.application.MarketDataProviderType;
import com.stockforecast.marketdata.application.ProviderUsageRecorder;
import com.stockforecast.marketdata.infrastructure.provider.ConditionalOnMarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated end-of-day prices for development and demos without a market-data subscription.
 *
 * <p>The data is <strong>not real</strong>: the provider reports {@code synthetic=true} so every client can label
 * it. Bars for a day become available after 21:00 UTC (after the US close), like an end-of-day feed. It cannot search
 * or describe securities, so administrators register stocks manually when it is active.
 */
@Component
@ConditionalOnMarketDataProvider(MarketDataProviderType.SYNTHETIC)
class SyntheticMarketDataProvider implements MarketDataProvider {

    static final String ID = MarketDataProviderType.SYNTHETIC.id();

    private static final Logger log = LoggerFactory.getLogger(SyntheticMarketDataProvider.class);
    private static final LocalTime END_OF_DAY_UTC = LocalTime.of(21, 0);
    private static final ProviderInfo INFO = new ProviderInfo(ID, MarketDataProviderType.SYNTHETIC.displayName(), true,
            "Simulated end-of-day prices for demonstration only. They do not reflect real market prices.");
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(
            Set.of(MarketDataInterval.ONE_DAY), false, false, true, Integer.MAX_VALUE,
            "Simulated prices without corporate actions: adjusted close equals close");

    private final Clock clock;
    private final ProviderUsageRecorder usage;
    private final ConcurrentHashMap<String, SyntheticPriceModel> models = new ConcurrentHashMap<>();

    SyntheticMarketDataProvider(Clock clock, ProviderUsageRecorder usage) {
        this.clock = clock;
        this.usage = usage;
        log.warn("Market data provider 'synthetic' is active: prices are SIMULATED and must not be used for "
                + "real decisions. Configure a licensed provider for production.");
    }

    @Override
    public ProviderInfo info() {
        return INFO;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public List<ProviderInstrumentMatch> searchStocks(String query) {
        return List.of();
    }

    @Override
    public Optional<ProviderStockMetadata> getStockMetadata(ProviderInstrument instrument) {
        return Optional.empty();
    }

    @Override
    public HistoricalPrices getHistoricalPrices(ProviderInstrument instrument, LocalDate from, LocalDate to,
                                                MarketDataInterval interval) {
        usage.recordSuccess(ID);
        return new HistoricalPrices(instrument.symbol(), null, MarketDataInterval.ONE_DAY,
                bars(instrument.symbol(), from, to).stream().map(SyntheticMarketDataProvider::toPrice).toList());
    }

    @Override
    public Optional<LatestQuote> getLatestQuote(ProviderInstrument instrument) {
        LocalDate latest = latestAvailableDate();
        List<DailyBar> recent = bars(instrument.symbol(), latest.minusDays(10), latest);
        if (recent.size() < 2) {
            return Optional.empty();
        }
        DailyBar last = recent.getLast();
        DailyBar previous = recent.get(recent.size() - 2);
        BigDecimal change = last.close().subtract(previous.close());
        BigDecimal changePercent = change.divide(previous.close(), 8, RoundingMode.HALF_UP);
        return Optional.of(new LatestQuote(instrument.symbol(), last.close(), change, changePercent,
                last.date().atTime(END_OF_DAY_UTC).toInstant(ZoneOffset.UTC)));
    }

    @Override
    public ProviderHealth checkHealth() {
        return new ProviderHealth(ID, ProviderHealth.Status.HEALTHY, clock.instant(), 0L,
                "Simulated data is always available", Map.of("synthetic", true));
    }

    List<DailyBar> bars(String symbol, LocalDate from, LocalDate to) {
        LocalDate latest = latestAvailableDate();
        LocalDate end = to.isAfter(latest) ? latest : to;
        return models.computeIfAbsent(symbol, SyntheticPriceModel::new).bars(from, end);
    }

    private static HistoricalPrice toPrice(DailyBar bar) {
        return new HistoricalPrice(bar.date(), bar.open(), bar.high(), bar.low(), bar.close(), bar.adjustedClose(),
                bar.volume());
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
