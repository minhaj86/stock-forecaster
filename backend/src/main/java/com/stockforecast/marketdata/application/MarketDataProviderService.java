package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataProviders;
import com.stockforecast.marketdata.ProviderCapabilities;
import com.stockforecast.marketdata.ProviderHealth;
import com.stockforecast.marketdata.ProviderStockMetadata;
import com.stockforecast.marketdata.application.MarketDataProvider.ProviderInstrument;
import com.stockforecast.marketdata.domain.MarketDataExceptions.MarketDataProviderRateLimitedException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.MarketDataProviderUnavailableException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.ProviderMetadataUnavailableException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.ProviderSymbolNotFoundException;
import com.stockforecast.shared.domain.DomainException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Provider administration for the admin console. Lookups call the provider synchronously (admin only, rate limited,
 * short timeouts); failures are translated into client-safe API errors.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class MarketDataProviderService implements MarketDataProviders {

    private static final String CONFIGURATION_HINT = "Set MARKET_DATA_PROVIDER (and TWELVE_DATA_API_KEY for Twelve "
            + "Data) and restart the backend to switch providers.";

    private final MarketDataProvider provider;
    private final MarketDataProviderHealthService healthService;
    private final ProviderUsageRecorder usage;
    private final ProviderThrottle throttle;
    private final MarketDataProperties properties;

    public MarketDataProviderService(MarketDataProvider provider, MarketDataProviderHealthService healthService,
                                     ProviderUsageRecorder usage, ProviderThrottle throttle,
                                     MarketDataProperties properties) {
        this.provider = provider;
        this.healthService = healthService;
        this.usage = usage;
        this.throttle = throttle;
        this.properties = properties;
    }

    @Override
    public ProviderOverview overview() {
        String activeId = provider.info().id();
        List<ProviderDescriptor> available = Arrays.stream(MarketDataProviderType.values())
                .map(type -> new ProviderDescriptor(type.id(), type.displayName(),
                        type == properties.providerType()))
                .toList();
        return new ProviderOverview(provider.info(), provider.requiresApiKey(),
                provider.requiresApiKey() && provider.isConfigured(), provider.capabilities(),
                healthService.lastKnown(), usage.today(activeId, throttle.status()), available, CONFIGURATION_HINT);
    }

    @Override
    public ProviderHealth health(boolean refresh) {
        return healthService.check(refresh);
    }

    @Override
    public ProviderHealth lastKnownHealth() {
        return healthService.lastKnown();
    }

    @Override
    public ProviderSearch search(String query) {
        ProviderCapabilities capabilities = provider.capabilities();
        if (!capabilities.symbolSearch()) {
            return new ProviderSearch(false, List.of());
        }
        try {
            return new ProviderSearch(true, provider.searchStocks(query.strip()));
        } catch (MarketDataProviderException e) {
            throw translate(e);
        }
    }

    @Override
    public ProviderStockMetadata metadata(String symbol, String mic) {
        if (!provider.capabilities().metadataLookup()) {
            throw new ProviderMetadataUnavailableException(provider.info().name());
        }
        String normalizedSymbol = symbol.strip().toUpperCase(Locale.ROOT);
        String normalizedMic = mic == null || mic.isBlank() ? null : mic.strip().toUpperCase(Locale.ROOT);
        try {
            return provider.getStockMetadata(new ProviderInstrument(normalizedSymbol, normalizedMic))
                    .orElseThrow(() -> new ProviderSymbolNotFoundException(normalizedSymbol, provider.info().name()));
        } catch (MarketDataProviderException e) {
            if (e.code() == ProviderErrorCode.SYMBOL_NOT_FOUND) {
                throw new ProviderSymbolNotFoundException(normalizedSymbol, provider.info().name());
            }
            throw translate(e);
        }
    }

    private DomainException translate(MarketDataProviderException e) {
        String name = provider.info().name();
        if (e.code() == ProviderErrorCode.RATE_LIMITED) {
            return new MarketDataProviderRateLimitedException(name, e.retryAfter().orElse(Duration.ofMinutes(1)));
        }
        return new MarketDataProviderUnavailableException(name, e.code().jobErrorCode(), e.getMessage(),
                e.isRetryable());
    }
}
