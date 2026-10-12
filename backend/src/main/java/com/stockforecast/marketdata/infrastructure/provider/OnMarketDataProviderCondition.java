package com.stockforecast.marketdata.infrastructure.provider;

import com.stockforecast.marketdata.application.MarketDataProviderType;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;
import java.util.Optional;

class OnMarketDataProviderCondition extends SpringBootCondition {

    static final String PROPERTY = "app.market-data.provider";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(
                ConditionalOnMarketDataProvider.class.getName());
        MarketDataProviderType required = attributes == null ? null
                : (MarketDataProviderType) attributes.get("value");
        String configured = context.getEnvironment().getProperty(PROPERTY);
        Optional<MarketDataProviderType> active = MarketDataProviderType.fromConfiguration(configured);
        if (required != null && active.filter(required::equals).isPresent()) {
            return ConditionOutcome.match("market-data provider is " + required.id());
        }
        return ConditionOutcome.noMatch("market-data provider is '" + configured + "', not " + required);
    }
}
