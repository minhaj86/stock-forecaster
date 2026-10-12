package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderCapabilities;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.ProviderInstrumentMatch;
import com.stockforecast.marketdata.ProviderStockMetadata;
import com.stockforecast.marketdata.application.MarketDataProvider;
import com.stockforecast.marketdata.application.MarketDataProviderException;
import com.stockforecast.marketdata.application.MarketDataProviderType;
import com.stockforecast.marketdata.application.ProviderErrorCode;
import com.stockforecast.marketdata.infrastructure.provider.twelvedata.TwelveDataDtos.ApiUsage;
import com.stockforecast.marketdata.infrastructure.provider.twelvedata.TwelveDataDtos.Quote;
import com.stockforecast.marketdata.infrastructure.provider.twelvedata.TwelveDataDtos.SymbolMatch;
import com.stockforecast.marketdata.infrastructure.provider.twelvedata.TwelveDataDtos.SymbolSearch;
import com.stockforecast.marketdata.infrastructure.provider.twelvedata.TwelveDataDtos.TimeSeries;
import com.stockforecast.marketdata.infrastructure.provider.twelvedata.TwelveDataDtos.Value;
import com.stockforecast.stock.AssetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Twelve Data adapter (https://twelvedata.com/docs). Vendor payloads are normalised here and nowhere else.
 *
 * <p>Price adjustment: every chunk is requested twice — {@code adjust=splits} for open/high/low/close/volume
 * (continuous trade prices) and {@code adjust=all} for the adjusted close (splits and dividends, a total-return series).
 * Each request costs one API credit. Daily bars of the current exchange-local day are only returned after 20:00
 * exchange time, so incremental updates never store a partial session.
 */
class TwelveDataMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataMarketDataProvider.class);
    private static final ProviderInfo INFO = new ProviderInfo(TwelveDataClient.PROVIDER_ID,
            MarketDataProviderType.TWELVE_DATA.displayName(), false,
            "Licensed end-of-day prices from Twelve Data (https://twelvedata.com).");
    private static final String ADJUSTMENT = "Open, high, low and close adjusted for splits; adjusted close adjusted "
            + "for splits and dividends";
    private static final LocalTime SESSION_FINAL_AFTER = LocalTime.of(20, 0);
    private static final int SEARCH_RESULTS = 20;
    private static final int METADATA_CANDIDATES = 120;

    private final TwelveDataClient client;
    private final TwelveDataProperties properties;
    private final Clock clock;

    TwelveDataMarketDataProvider(TwelveDataClient client, TwelveDataProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        if (!properties.hasApiKey()) {
            log.warn("Market-data provider Twelve Data is active but TWELVE_DATA_API_KEY is missing: synchronisation "
                    + "will fail until it is configured");
        }
    }

    @Override
    public ProviderInfo info() {
        return INFO;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(Set.of(MarketDataInterval.ONE_DAY), true, true, true,
                properties.maxBarsPerRequest(), ADJUSTMENT);
    }

    @Override
    public boolean isConfigured() {
        return properties.hasApiKey();
    }

    @Override
    public List<ProviderInstrumentMatch> searchStocks(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return client.get("/symbol_search", Map.of("symbol", query.strip(),
                        "outputsize", Integer.toString(SEARCH_RESULTS)), SymbolSearch.class, true)
                .map(SymbolSearch::data).orElse(List.of()).stream()
                .filter(match -> match.symbol() != null && !match.symbol().isBlank())
                .map(TwelveDataMarketDataProvider::toMatch)
                .toList();
    }

    @Override
    public Optional<ProviderStockMetadata> getStockMetadata(ProviderInstrument instrument) {
        List<SymbolMatch> candidates = client.get("/symbol_search", Map.of("symbol", instrument.symbol(),
                        "outputsize", Integer.toString(METADATA_CANDIDATES)), SymbolSearch.class, true)
                .map(SymbolSearch::data).orElse(List.of());
        return candidates.stream()
                .filter(match -> instrument.symbol().equalsIgnoreCase(match.symbol()))
                .filter(match -> instrument.mic() == null || instrument.mic().equalsIgnoreCase(match.micCode()))
                .findFirst()
                .map(match -> {
                    ProviderInstrumentMatch normalized = toMatch(match);
                    return new ProviderStockMetadata(normalized.symbol(), normalized.name(), normalized.exchange(),
                            normalized.mic(), normalized.currency(), normalized.country(),
                            normalized.instrumentType(), normalized.assetType(), null, null);
                });
    }

    @Override
    public HistoricalPrices getHistoricalPrices(ProviderInstrument instrument, LocalDate from, LocalDate to,
                                                MarketDataInterval interval) {
        if (interval != MarketDataInterval.ONE_DAY) {
            throw new MarketDataProviderException(ProviderErrorCode.INVALID_REQUEST,
                    "Twelve Data adapter only stores daily bars");
        }
        List<HistoricalPrice> prices = new ArrayList<>();
        String symbol = null;
        String currency = null;
        String timezone = null;
        long chunkDays = Math.max(30, (long) (properties.maxBarsPerRequest() * 0.9 * 7 / 5));
        for (LocalDate start = from; !start.isAfter(to); start = start.plusDays(chunkDays)) {
            LocalDate end = start.plusDays(chunkDays - 1).isAfter(to) ? to : start.plusDays(chunkDays - 1);
            Optional<TimeSeries> trade = timeSeries(instrument, start, end, "splits");
            if (trade.isEmpty() || trade.get().values() == null || trade.get().values().isEmpty()) {
                continue;
            }
            Map<LocalDate, BigDecimal> adjustedCloses = new HashMap<>();
            timeSeries(instrument, start, end, "all").map(TimeSeries::values).orElse(List.of()).forEach(value ->
                    parseDate(value.datetime()).ifPresent(date -> adjustedCloses.put(date, decimal(value.close()))));
            if (trade.get().meta() != null) {
                symbol = trade.get().meta().symbol();
                currency = trade.get().meta().currency();
                timezone = trade.get().meta().exchangeTimezone();
            }
            for (Value value : trade.get().values()) {
                LocalDate date = parseDate(value.datetime()).orElse(null);
                if (date != null && (date.isBefore(start) || date.isAfter(end))) {
                    continue;
                }
                // Duplicates and undated bars are kept: validation rejects them explicitly.
                prices.add(new HistoricalPrice(date, decimal(value.open()), decimal(value.high()),
                        decimal(value.low()), decimal(value.close()), date == null ? null : adjustedCloses.get(date),
                        volume(value.volume())));
            }
        }
        LocalDate lastFinalSession = lastFinalSession(timezone);
        prices.removeIf(price -> price.date() != null && price.date().isAfter(lastFinalSession));
        prices.sort(Comparator.comparing(HistoricalPrice::date, Comparator.nullsFirst(Comparator.naturalOrder())));
        return new HistoricalPrices(symbol == null ? instrument.symbol() : symbol, currency, interval, prices);
    }

    @Override
    public Optional<LatestQuote> getLatestQuote(ProviderInstrument instrument) {
        Map<String, String> query = new HashMap<>(Map.of("symbol", instrument.symbol()));
        if (instrument.mic() != null) {
            query.put("mic_code", instrument.mic());
        }
        return client.get("/quote", query, Quote.class, true).flatMap(quote -> {
            BigDecimal price = decimal(quote.close());
            if (price == null) {
                return Optional.empty();
            }
            BigDecimal percent = decimal(quote.percentChange());
            Long epochSeconds = quote.lastQuoteAt() != null ? quote.lastQuoteAt() : quote.timestamp();
            return Optional.of(new LatestQuote(instrument.symbol(), price, decimal(quote.change()),
                    percent == null ? null : percent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP),
                    epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds)));
        });
    }

    @Override
    public ProviderHealth checkHealth() {
        long started = System.nanoTime();
        ApiUsage usage = client.get("/api_usage", Map.of(), ApiUsage.class, false).orElseThrow();
        long latency = (System.nanoTime() - started) / 1_000_000;
        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "planCategory", usage.planCategory());
        putIfPresent(details, "minuteUsage", usage.currentUsage());
        putIfPresent(details, "minuteLimit", usage.planLimit());
        putIfPresent(details, "dailyUsage", usage.dailyUsage());
        putIfPresent(details, "dailyLimit", usage.planDailyLimit());
        boolean minuteExhausted = usage.currentUsage() != null && usage.planLimit() != null
                && usage.currentUsage() >= usage.planLimit();
        boolean dailyNearlyExhausted = usage.dailyUsage() != null && usage.planDailyLimit() != null
                && usage.dailyUsage() >= usage.planDailyLimit() * 0.95;
        ProviderHealth.Status status = minuteExhausted || dailyNearlyExhausted ? ProviderHealth.Status.DEGRADED
                : ProviderHealth.Status.HEALTHY;
        String message = dailyNearlyExhausted ? "Provider reachable, but the daily request quota is nearly used up"
                : minuteExhausted ? "Provider reachable, but this minute's requests are used up"
                : usage.currentUsage() != null && usage.planLimit() != null
                ? "Provider reachable; %d of %d requests used this minute".formatted(usage.currentUsage(),
                usage.planLimit())
                : "Provider reachable";
        return new ProviderHealth(TwelveDataClient.PROVIDER_ID, status, clock.instant(), latency, message, details);
    }

    private Optional<TimeSeries> timeSeries(ProviderInstrument instrument, LocalDate start, LocalDate end,
                                            String adjust) {
        Map<String, String> query = new HashMap<>();
        query.put("symbol", instrument.symbol());
        query.put("interval", "1day");
        // end_date is treated as exclusive by the vendor for some instruments: ask one day more, filter afterwards.
        query.put("start_date", start.toString());
        query.put("end_date", end.plusDays(1).toString());
        query.put("order", "asc");
        query.put("adjust", adjust);
        query.put("outputsize", Integer.toString(properties.maxBarsPerRequest()));
        if (instrument.mic() != null) {
            query.put("mic_code", instrument.mic());
        }
        try {
            return client.get("/time_series", query, TimeSeries.class, true);
        } catch (MarketDataProviderException e) {
            if (e.code() != ProviderErrorCode.SYMBOL_NOT_FOUND || instrument.mic() == null) {
                throw e;
            }
            // The catalogue may hold the operating MIC (XNAS) while the vendor lists a segment MIC (XNGS); the
            // currency and symbol checks of the validation guard against picking another listing.
            log.info("Twelve Data does not list {} on {}; retrying without the market identifier",
                    instrument.symbol(), instrument.mic());
            query.remove("mic_code");
            return client.get("/time_series", query, TimeSeries.class, true);
        }
    }

    private LocalDate lastFinalSession(String exchangeTimezone) {
        ZoneId zone;
        try {
            zone = exchangeTimezone == null ? ZoneOffset.UTC : ZoneId.of(exchangeTimezone);
        } catch (DateTimeException e) {
            zone = ZoneOffset.UTC;
        }
        ZonedDateTime local = ZonedDateTime.now(clock.withZone(zone));
        return local.toLocalTime().isBefore(SESSION_FINAL_AFTER) ? local.toLocalDate().minusDays(1)
                : local.toLocalDate();
    }

    private static ProviderInstrumentMatch toMatch(SymbolMatch match) {
        return new ProviderInstrumentMatch(match.symbol().strip().toUpperCase(Locale.ROOT), match.instrumentName(),
                match.exchange(), upper(match.micCode()), upper(match.currency()),
                CountryCodes.toIsoCode(match.country()).orElse(null), match.instrumentType(),
                assetType(match.instrumentType()));
    }

    static AssetType assetType(String instrumentType) {
        if (instrumentType == null) {
            return null;
        }
        return switch (instrumentType.strip().toLowerCase(Locale.ROOT)) {
            case "common stock" -> AssetType.COMMON_STOCK;
            case "etf" -> AssetType.ETF;
            case "american depositary receipt", "depositary receipt", "global depositary receipt" -> AssetType.ADR;
            default -> null;
        };
    }

    private static Optional<LocalDate> parseDate(String datetime) {
        if (datetime == null || datetime.length() < 10) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(datetime.substring(0, 10)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long volume(String value) {
        BigDecimal decimal = decimal(value);
        if (decimal == null) {
            return null;
        }
        try {
            return decimal.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static void putIfPresent(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }
}
