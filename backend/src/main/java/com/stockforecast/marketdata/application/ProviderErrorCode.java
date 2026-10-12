package com.stockforecast.marketdata.application;

/**
 * Normalised classification of provider failures. {@link #jobErrorCode()} is the stable code stored on jobs.
 */
public enum ProviderErrorCode {
    TIMEOUT("PROVIDER_TIMEOUT", true),
    RATE_LIMITED("PROVIDER_RATE_LIMITED", true),
    UNAVAILABLE("PROVIDER_UNAVAILABLE", true),
    AUTHENTICATION_FAILED("PROVIDER_AUTHENTICATION_FAILED", false),
    PLAN_RESTRICTED("PROVIDER_PLAN_RESTRICTED", false),
    NOT_CONFIGURED("PROVIDER_NOT_CONFIGURED", false),
    SYMBOL_NOT_FOUND("SYMBOL_NOT_FOUND", false),
    INVALID_REQUEST("PROVIDER_REJECTED_REQUEST", false),
    INVALID_RESPONSE("INVALID_PROVIDER_RESPONSE", false);

    private final String jobErrorCode;
    private final boolean transientFailure;

    ProviderErrorCode(String jobErrorCode, boolean transientFailure) {
        this.jobErrorCode = jobErrorCode;
        this.transientFailure = transientFailure;
    }

    public String jobErrorCode() {
        return jobErrorCode;
    }

    /** Retrying later may succeed (timeouts, rate limits, 5xx). */
    public boolean isTransient() {
        return transientFailure;
    }
}
