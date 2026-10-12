package com.stockforecast.marketdata.infrastructure.provider.twelvedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Twelve Data payloads. Package-private: vendor types never leave the adapter. Numbers arrive as strings.
 */
final class TwelveDataDtos {

    private TwelveDataDtos() {
    }

    /** Error envelope; Twelve Data may also return it with HTTP 200. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(Integer code, String message, String status) {

        boolean isError() {
            return "error".equalsIgnoreCase(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSeries(Meta meta, List<Value> values, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(String symbol, String interval, String currency,
                @JsonProperty("exchange_timezone") String exchangeTimezone, String exchange,
                @JsonProperty("mic_code") String micCode, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Value(String datetime, String open, String high, String low, String close, String volume) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SymbolSearch(List<SymbolMatch> data, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SymbolMatch(String symbol, @JsonProperty("instrument_name") String instrumentName, String exchange,
                       @JsonProperty("mic_code") String micCode,
                       @JsonProperty("exchange_timezone") String exchangeTimezone,
                       @JsonProperty("instrument_type") String instrumentType, String country, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Quote(String symbol, String name, String exchange, @JsonProperty("mic_code") String micCode,
                 String currency, String close, String change, @JsonProperty("percent_change") String percentChange,
                 Long timestamp, @JsonProperty("last_quote_at") Long lastQuoteAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiUsage(String timestamp, @JsonProperty("current_usage") Integer currentUsage,
                    @JsonProperty("plan_limit") Integer planLimit, @JsonProperty("daily_usage") Integer dailyUsage,
                    @JsonProperty("plan_daily_limit") Integer planDailyLimit,
                    @JsonProperty("plan_category") String planCategory) {
    }
}
