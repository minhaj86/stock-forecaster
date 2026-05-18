package com.stockforecast.shared.api;

import com.stockforecast.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link ApiError} instances with consistent timestamp, path and correlation id handling.
 */
@Component
public class ApiErrorFactory {

    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError create(HttpStatusCode status, String code, String message, HttpServletRequest request) {
        return create(status, code, message, request, List.of());
    }

    public ApiError create(HttpStatusCode status, String code, String message, HttpServletRequest request,
                           List<ApiError.FieldViolation> violations) {
        return create(status, code, message, request, violations, Map.of());
    }

    public ApiError create(HttpStatusCode status, String code, String message, HttpServletRequest request,
                           List<ApiError.FieldViolation> violations, Map<String, Object> details) {
        return new ApiError(clock.instant(), status.value(), code, message, request.getRequestURI(),
                CorrelationIdFilter.currentCorrelationId(request), violations, details);
    }
}
