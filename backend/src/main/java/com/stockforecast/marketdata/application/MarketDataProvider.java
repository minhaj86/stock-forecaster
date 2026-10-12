package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.PriceHistory.ProviderInfo;
import com.stockforecast.marketdata.ProviderCapabilities;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.ProviderInstrumentMatch;
import com.stockforecast.marketdata.ProviderStockMetadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Port to an external market-data vendor. Adapters translate vendor payloads into these types immediately; vendor
 * types never leave the adapter package. Implementations must be safe to call concurrently, respect the vendor's rate
 * limits ({@link ProviderThrottle}) and report failures as {@link MarketDataProviderException}.
 *
 * <p>Price semantics every adapter must deliver (analytics and forecasting rely on them): open, high, low and close are
 * adjusted for splits only (continuous trade prices); {@code adjustedClose} is additionally adjusted for dividends
 * (total-return series used for returns, volatility and forecasts). Only completed sessions are returned.
 */
public interface MarketDataProvider {

    /** Identity of the provider; {@code synthetic} providers must be labelled as simulated in every UI. */
    ProviderInfo info();

    ProviderCapabilities capabilities();

    /** {@code false} when required configuration (such as an API key) is missing; calls then fail. */
    boolean isConfigured();

    /** Whether the vendor needs an API key at all (the synthetic demo provider does not). */
    default boolean requiresApiKey() {
        return true;
    }

    /**
     * @return matches ordered by relevance; empty if nothing matches or search is not supported
     */
    List<ProviderInstrumentMatch> searchStocks(String query);

    /**
     * @return listing metadata, or empty if the provider does not know the instrument
     */
    Optional<ProviderStockMetadata> getStockMetadata(ProviderInstrument instrument);

    /**
     * Bars between both dates inclusive, oldest first, unvalidated (see {@code MarketDataValidationService}); empty
     * for periods without trading. Long ranges are split into several vendor requests by the adapter.
     */
    HistoricalPrices getHistoricalPrices(ProviderInstrument instrument, LocalDate from, LocalDate to,
                                         MarketDataInterval interval);

    Optional<LatestQuote> getLatestQuote(ProviderInstrument instrument);

    /**
     * Cheap active check of reachability and credentials (may use one vendor request).
     */
    ProviderHealth checkHealth();

    /**
     * @param mic ISO 10383 market identifier code disambiguating listings of the same ticker, or {@code null}
     */
    record ProviderInstrument(String symbol, String mic) {

        public ProviderInstrument {
            Objects.requireNonNull(symbol, "symbol");
        }
    }

    /**
     * A provider response for one instrument. {@code symbol} and {@code currency} are what the provider says the data
     * is about (validated against the catalogue before anything is stored); either may be {@code null} if the vendor
     * does not report it.
     */
    record HistoricalPrices(String symbol, String currency, MarketDataInterval interval,
                            List<HistoricalPrice> prices) {

        public HistoricalPrices {
            prices = List.copyOf(prices);
        }
    }

    /**
     * One raw bar as delivered by the provider; any value may be missing or inconsistent until validated.
     */
    record HistoricalPrice(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                           BigDecimal adjustedClose, Long volume) {
    }

    /**
     * @param changePercent change versus the previous close as a decimal fraction (0.012 = 1.2 %)
     */
    record LatestQuote(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent,
                       Instant timestamp) {
    }
}
