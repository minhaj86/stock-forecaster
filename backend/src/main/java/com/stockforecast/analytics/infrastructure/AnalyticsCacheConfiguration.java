package com.stockforecast.analytics.infrastructure;

import com.stockforecast.analytics.application.StockAnalyticsService;
import com.stockforecast.analytics.application.StockSnapshot;
import com.stockforecast.shared.config.CacheConfiguration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class AnalyticsCacheConfiguration {

    /** Keys embed the data version, so the TTL only bounds memory, not staleness. */
    @Bean
    RedisCacheManagerBuilderCustomizer analyticsCaches(JsonMapper jsonMapper) {
        return builder -> builder.withCacheConfiguration(StockAnalyticsService.SNAPSHOT_CACHE,
                CacheConfiguration.typedJsonCache(builder.cacheDefaults(), jsonMapper,
                        jsonMapper.constructType(StockSnapshot.class), Duration.ofHours(12)));
    }
}
