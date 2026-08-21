package com.stockforecast.forecasting.infrastructure;

import com.stockforecast.forecasting.application.ForecastProperties;
import com.stockforecast.forecasting.application.ForecastService;
import com.stockforecast.forecasting.application.ForecastViews.ForecastResult;
import com.stockforecast.forecasting.domain.backtest.BacktestResult;
import com.stockforecast.shared.config.CacheConfiguration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ForecastCacheConfiguration {

    /** Keys embed the data version and settings, so the TTL only bounds memory, not staleness. */
    @Bean
    RedisCacheManagerBuilderCustomizer forecastCaches(JsonMapper jsonMapper, ForecastProperties properties) {
        return builder -> builder
                .withCacheConfiguration(ForecastService.FORECAST_CACHE, CacheConfiguration.typedJsonCache(
                        builder.cacheDefaults(), jsonMapper, jsonMapper.constructType(ForecastResult.class),
                        properties.cacheTimeToLive()))
                .withCacheConfiguration(ForecastService.BACKTEST_CACHE, CacheConfiguration.typedJsonCache(
                        builder.cacheDefaults(), jsonMapper, jsonMapper.constructType(BacktestResult.class),
                        properties.cacheTimeToLive()));
    }
}
