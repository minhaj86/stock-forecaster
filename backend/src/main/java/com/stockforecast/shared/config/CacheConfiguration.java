package com.stockforecast.shared.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * Caching is an optimisation, never a dependency: cache failures (e.g. Redis unavailable) are logged and the
 * underlying method runs as if the cache were empty.
 *
 * <p>Modules declare their caches (name, value type, TTL) next to the cached code with
 * {@link #typedJsonCache(RedisCacheConfiguration, JsonMapper, JavaType, Duration)}. Values are stored as JSON
 * with an explicit type per cache, so records need no polymorphic type metadata and payloads stay readable.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfiguration implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }

    public static RedisCacheConfiguration typedJsonCache(RedisCacheConfiguration defaults, JsonMapper jsonMapper,
                                                         JavaType valueType, Duration ttl) {
        return defaults.entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, valueType)));
    }
}
