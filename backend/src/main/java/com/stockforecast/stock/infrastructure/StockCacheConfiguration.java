package com.stockforecast.stock.infrastructure;

import com.stockforecast.shared.config.CacheConfiguration;
import com.stockforecast.stock.StockRef;
import com.stockforecast.stock.application.StockCatalogService;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
class StockCacheConfiguration {

    @Bean
    RedisCacheManagerBuilderCustomizer stockCaches(JsonMapper jsonMapper) {
        return builder -> builder.withCacheConfiguration(StockCatalogService.SEARCH_CACHE,
                CacheConfiguration.typedJsonCache(builder.cacheDefaults(), jsonMapper,
                        jsonMapper.getTypeFactory().constructCollectionType(List.class, StockRef.class),
                        Duration.ofHours(1)));
    }
}
