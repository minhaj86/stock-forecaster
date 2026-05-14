package com.stockforecast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Modular monolith entry point. Every direct sub-package is an application module with its own
 * domain, application, infrastructure and api layers; boundaries are verified by Spring Modulith tests.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StockForecastApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockForecastApplication.class, args);
    }
}
