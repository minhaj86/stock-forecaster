package com.stockforecast.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uniform error envelope returned by every endpoint, including security and container-level errors.
 */
@Schema(name = "ApiError", description = "Uniform error response")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        @Schema(description = "Stable machine-readable error code", example = "VALIDATION_FAILED") String code,
        @Schema(description = "Human-readable, client-safe description") String message,
        String path,
        @Schema(description = "Correlates the response with server logs") String correlationId,
        @Schema(description = "Field-level validation problems, if any") List<FieldViolation> errors,
        @Schema(description = "Structured, error-specific values (e.g. required and available history)")
        Map<String, Object> details) {

    public ApiError {
        errors = errors == null ? List.of() : List.copyOf(errors);
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public ApiError(Instant timestamp, int status, String code, String message, String path, String correlationId,
                    List<FieldViolation> errors) {
        this(timestamp, status, code, message, path, correlationId, errors, Map.of());
    }

    public record FieldViolation(String field, String message) {
    }
}
