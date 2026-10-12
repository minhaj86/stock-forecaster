package com.stockforecast.marketdata.infrastructure.provider;

import com.stockforecast.marketdata.application.MarketDataProviderType;
import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers the annotated adapter (and its supporting beans) only when it is the configured provider
 * ({@code app.market-data.provider}, accepting {@code twelve-data} as well as {@code TWELVE_DATA}).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnMarketDataProviderCondition.class)
public @interface ConditionalOnMarketDataProvider {

    MarketDataProviderType value();
}
