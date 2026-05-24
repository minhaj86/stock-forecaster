package com.stockforecast.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL for integration tests (same major version as docker-compose). The container is a Spring bean,
 * so cached test contexts share it and it stops with the context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    public static final String POSTGRES_IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
