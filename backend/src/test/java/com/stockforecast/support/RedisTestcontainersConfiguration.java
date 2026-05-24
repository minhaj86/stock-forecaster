package com.stockforecast.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Redis for integration tests, so cache serialisation is exercised exactly as in production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {

    public static final String REDIS_IMAGE = "redis:8-alpine";

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);
    }
}
