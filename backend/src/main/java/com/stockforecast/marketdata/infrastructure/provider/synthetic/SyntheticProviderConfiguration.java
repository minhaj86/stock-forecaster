package com.stockforecast.marketdata.infrastructure.provider.synthetic;

import com.stockforecast.marketdata.application.MarketDataProviderType;
import com.stockforecast.marketdata.application.ProviderThrottle;
import com.stockforecast.marketdata.infrastructure.provider.ConditionalOnMarketDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMarketDataProvider(MarketDataProviderType.SYNTHETIC)
class SyntheticProviderConfiguration {

    /** Simulated data costs nothing: no client-side rate limit. */
    @Bean
    ProviderThrottle syntheticProviderThrottle() {
        return ProviderThrottle.UNLIMITED;
    }
}
