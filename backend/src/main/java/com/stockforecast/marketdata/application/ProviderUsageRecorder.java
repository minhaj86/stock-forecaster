package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.MarketDataProviders.ProviderUsage;
import com.stockforecast.marketdata.MarketDataProviders.RateLimitStatus;

/**
 * Daily request statistics per provider, shared by all instances. Recording never fails the provider call.
 */
public interface ProviderUsageRecorder {

    void recordSuccess(String provider);

    /**
     * @param message client-safe description (never contains credentials)
     */
    void recordFailure(String provider, ProviderErrorCode code, String message);

    ProviderUsage today(String provider, RateLimitStatus rateLimit);
}
