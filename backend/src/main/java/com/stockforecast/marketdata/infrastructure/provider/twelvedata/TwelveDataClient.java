package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import com.stockforecast.marketdata.application.MarketDataProviderException;
import com.stockforecast.marketdata.application.ProviderErrorCode;
import com.stockforecast.marketdata.application.ProviderThrottle;
import com.stockforecast.marketdata.application.ProviderUsageRecorder;
import com.stockforecast.marketdata.infrastructure.provider.twelvedata.TwelveDataDtos.ErrorBody;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * HTTP access to Twelve Data: rate limiting (one permit per request, i.e. per API credit), error classification,
 * usage statistics and metrics. The API key travels in the {@code Authorization} header, so it never appears in URLs
 * or logs. Twelve Data reports errors as {@code {code, message, status: "error"}} — sometimes with HTTP 200 — so the
 * body is inspected as well as the status code.
 */
class TwelveDataClient {

    static final String PROVIDER_ID = "twelve-data";

    private static final Logger log = LoggerFactory.getLogger(TwelveDataClient.class);
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final int MAX_VENDOR_MESSAGE = 200;
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final TwelveDataProperties properties;
    private final ProviderThrottle throttle;
    private final ProviderUsageRecorder usage;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    TwelveDataClient(RestClient restClient, JsonMapper jsonMapper, TwelveDataProperties properties,
                     ProviderThrottle throttle, ProviderUsageRecorder usage, MeterRegistry meterRegistry,
                     Clock clock) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.throttle = throttle;
        this.usage = usage;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * @param emptyOnNoData treat "no data is available for the requested dates" as an empty result
     * @return the parsed body, or empty for a "no data" answer
     */
    <T> Optional<T> get(String endpoint, Map<String, String> query, Class<T> type, boolean emptyOnNoData) {
        if (!properties.hasApiKey()) {
            throw new MarketDataProviderException(ProviderErrorCode.NOT_CONFIGURED,
                    "Twelve Data is not configured: TWELVE_DATA_API_KEY is missing");
        }
        throttle.acquire(1, properties.maxWait());
        RawResponse response = execute(endpoint, query);
        ErrorBody error = errorBody(response.body());
        boolean errorPayload = error != null && error.isError();
        int status = errorPayload && error.code() != null ? error.code() : response.status();
        if (!errorPayload && status >= 200 && status < 300) {
            T value = parse(endpoint, response.body(), type);
            recordSuccess(endpoint);
            return Optional.of(value);
        }
        String vendorMessage = sanitize(error == null ? null : error.message());
        if (emptyOnNoData && (status == 400 || status == 404)
                && vendorMessage.toLowerCase(Locale.ROOT).contains("no data")) {
            recordSuccess(endpoint);
            return Optional.empty();
        }
        ProviderErrorCode code = classify(status, vendorMessage);
        Duration retryAfter = null;
        if (code == ProviderErrorCode.RATE_LIMITED) {
            retryAfter = retryAfter(response.retryAfter(), vendorMessage);
            throttle.pauseFor(retryAfter);
            meterRegistry.counter("market_data_provider_rate_limit", "provider", PROVIDER_ID, "source", "provider")
                    .increment();
        }
        throw failure(endpoint, code, describe(code, status, vendorMessage), retryAfter, null);
    }

    private RawResponse execute(String endpoint, Map<String, String> query) {
        try {
            return restClient.get()
                    .uri(builder -> {
                        builder.path(endpoint);
                        query.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .exchange((request, response) -> new RawResponse(response.getStatusCode().value(),
                            response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), readBody(response.getBody())));
        } catch (ResourceAccessException e) {
            boolean timeout = hasCause(e, HttpTimeoutException.class) || hasCause(e, SocketTimeoutException.class);
            throw failure(endpoint, timeout ? ProviderErrorCode.TIMEOUT : ProviderErrorCode.UNAVAILABLE,
                    timeout ? "Twelve Data did not respond in time" : "Twelve Data could not be reached", null, e);
        }
    }

    private static String readBody(InputStream body) throws IOException {
        return body == null ? "" : new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private ErrorBody errorBody(String body) {
        try {
            JsonNode node = jsonMapper.readTree(body);
            return node != null && node.isObject() && node.has("status")
                    ? jsonMapper.treeToValue(node, ErrorBody.class) : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private <T> T parse(String endpoint, String body, Class<T> type) {
        try {
            T value = body == null || body.isBlank() ? null : jsonMapper.readValue(body, type);
            if (value == null) {
                throw failure(endpoint, ProviderErrorCode.INVALID_RESPONSE, "Twelve Data returned an empty response",
                        null, null);
            }
            return value;
        } catch (JacksonException e) {
            throw failure(endpoint, ProviderErrorCode.INVALID_RESPONSE, "Twelve Data returned a malformed response",
                    null, e);
        }
    }

    static ProviderErrorCode classify(int status, String vendorMessage) {
        String message = vendorMessage.toLowerCase(Locale.ROOT);
        return switch (status) {
            case 401 -> ProviderErrorCode.AUTHENTICATION_FAILED;
            case 403 -> ProviderErrorCode.PLAN_RESTRICTED;
            case 404 -> ProviderErrorCode.SYMBOL_NOT_FOUND;
            case 429 -> ProviderErrorCode.RATE_LIMITED;
            case 400 -> message.contains("symbol") && (message.contains("not found") || message.contains("invalid")
                    || message.contains("missing")) ? ProviderErrorCode.SYMBOL_NOT_FOUND
                    : message.contains("apikey") ? ProviderErrorCode.AUTHENTICATION_FAILED
                    : ProviderErrorCode.INVALID_REQUEST;
            default -> status >= 500 ? ProviderErrorCode.UNAVAILABLE : ProviderErrorCode.INVALID_REQUEST;
        };
    }

    private static String describe(ProviderErrorCode code, int status, String vendorMessage) {
        String summary = switch (code) {
            case AUTHENTICATION_FAILED -> "Twelve Data rejected the API key";
            case PLAN_RESTRICTED -> "The Twelve Data plan does not include this data";
            case SYMBOL_NOT_FOUND -> "Twelve Data does not know this symbol";
            case RATE_LIMITED -> "Twelve Data rate limit reached";
            case UNAVAILABLE -> "Twelve Data is temporarily unavailable (HTTP " + status + ")";
            default -> "Twelve Data rejected the request (HTTP " + status + ")";
        };
        return vendorMessage.isBlank() ? summary : summary + ": " + vendorMessage;
    }

    private Duration retryAfter(String header, String vendorMessage) {
        if (header != null) {
            try {
                long seconds = Long.parseLong(header.strip());
                if (seconds > 0) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException e) {
                log.debug("Ignoring non-numeric Retry-After header from Twelve Data");
            }
        }
        if (vendorMessage.toLowerCase(Locale.ROOT).contains("for the day")) {
            Instant now = clock.instant();
            Instant nextDay = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC)
                    .toInstant().plusSeconds(60);
            return Duration.between(now, nextDay);
        }
        return DEFAULT_RETRY_AFTER;
    }

    /** Vendor text is shown to administrators: drop links and markup, never echo the key, keep it short. */
    private String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String cleaned = URL.matcher(message).replaceAll("").replace("**", "").strip();
        if (properties.hasApiKey()) {
            cleaned = cleaned.replace(properties.apiKey(), "***");
        }
        return cleaned.length() > MAX_VENDOR_MESSAGE ? cleaned.substring(0, MAX_VENDOR_MESSAGE) + "…" : cleaned;
    }

    private void recordSuccess(String endpoint) {
        usage.recordSuccess(PROVIDER_ID);
        meterRegistry.counter("market_data_provider_requests", "provider", PROVIDER_ID, "endpoint", endpoint,
                "outcome", "success").increment();
    }

    private MarketDataProviderException failure(String endpoint, ProviderErrorCode code, String message,
                                                Duration retryAfter, Throwable cause) {
        usage.recordFailure(PROVIDER_ID, code, message);
        meterRegistry.counter("market_data_provider_requests", "provider", PROVIDER_ID, "endpoint", endpoint,
                "outcome", "failure").increment();
        meterRegistry.counter("market_data_provider_errors", "provider", PROVIDER_ID, "code", code.name())
                .increment();
        log.warn("Twelve Data {} request failed ({}): {}", endpoint, code, message);
        return new MarketDataProviderException(code, message, retryAfter, cause);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private record RawResponse(int status, String retryAfter, String body) {
    }
}
