package com.stockforecast.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full application context against PostgreSQL and Redis, exercised through MockMvc with the complete servlet filter chain
 * (correlation id, CORS, Spring Security). All integration tests share one cached context and container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class, AuthTestClient.class,
        WatchlistTestClient.class})
public @interface IntegrationTest {
}
