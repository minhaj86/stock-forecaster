package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import com.stockforecast.marketdata.application.MarketDataProvider;
import com.stockforecast.marketdata.application.MarketDataProviderType;
import com.stockforecast.marketdata.application.ProviderThrottle;
import com.stockforecast.marketdata.application.ProviderUsageRecorder;
import com.stockforecast.marketdata.infrastructure.provider.Bucket4jProviderThrottle;
import com.stockforecast.marketdata.infrastructure.provider.ConditionalOnMarketDataProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;

/**
 * Wires the Twelve Data adapter when {@code MARKET_DATA_PROVIDER=TWELVE_DATA}: a dedicated HTTP client with connect
 * and read timeouts, and a token bucket sized from the plan's requests per minute.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMarketDataProvider(MarketDataProviderType.TWELVE_DATA)
@EnableConfigurationProperties(TwelveDataProperties.class)
class TwelveDataConfiguration {

    @Bean
    ProviderThrottle twelveDataThrottle(TwelveDataProperties properties, Clock clock, MeterRegistry meterRegistry) {
        return new Bucket4jProviderThrottle(TwelveDataClient.PROVIDER_ID, properties.requestsPerMinute(), clock,
                meterRegistry);
    }

    @Bean
    TwelveDataClient twelveDataClient(RestClient.Builder builder, JsonMapper jsonMapper,
                                      TwelveDataProperties properties, ProviderThrottle twelveDataThrottle,
                                      ProviderUsageRecorder usage, MeterRegistry meterRegistry, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient.Builder client = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.hasApiKey()) {
            client.defaultHeader(HttpHeaders.AUTHORIZATION, "apikey " + properties.apiKey());
        }
        return new TwelveDataClient(client.build(), jsonMapper, properties, twelveDataThrottle, usage, meterRegistry,
                clock);
    }

    @Bean
    MarketDataProvider twelveDataMarketDataProvider(TwelveDataClient client, TwelveDataProperties properties,
                                                    Clock clock) {
        return new TwelveDataMarketDataProvider(client, properties, clock);
    }
}
