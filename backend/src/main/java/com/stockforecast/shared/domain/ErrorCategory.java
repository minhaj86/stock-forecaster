package com.stockforecast.shared.domain;

/**
 * Transport-neutral classification of a domain failure. The API layer maps categories to HTTP status codes,
 * which keeps domain and application code free of web concerns.
 */
public enum ErrorCategory {
    VALIDATION,
    /** The request is well-formed but cannot be processed with the current data (HTTP 422). */
    UNPROCESSABLE,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    UNAVAILABLE
}
