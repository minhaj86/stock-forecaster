package com.stockforecast.shared.api;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Central translation of exceptions into {@link ApiError} responses.
 *
 * <p>Expected failures (domain, validation, framework 4xx) are logged at debug level; anything else is an
 * unexpected server error, logged with its stack trace and returned as a generic message so internals never
 * leak to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String INTERNAL_ERROR_MESSAGE =
            "An unexpected error occurred. Retry later or contact support quoting the correlation id.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorFactory errorFactory;

    public GlobalExceptionHandler(ApiErrorFactory errorFactory) {
        this.errorFactory = errorFactory;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> handleDomainException(DomainException ex, HttpServletRequest request) {
        HttpStatus status = toHttpStatus(ex.category());
        log.debug("Domain failure {} on {} {}: {}", ex.code(), request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return ResponseEntity.status(status).body(
                errorFactory.create(status, ex.code(), ex.getMessage(), request, List.of(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        List<ApiError.FieldViolation> globalViolations = ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getObjectName(), error.getDefaultMessage()))
                .toList();
        return validationFailed(request, concat(violations, globalViolations));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleInvalidParameters(HandlerMethodValidationException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return validationFailed(request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                       HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return validationFailed(request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed",
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parameter '%s' has an invalid value".formatted(ex.getName()), request, List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Concurrent modification on {} {}", request.getMethod(), request.getRequestURI());
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently; reload it and retry", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data",
                request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource", request, List.of());
    }

    /**
     * Method-security denials ({@code @PreAuthorize}) are raised inside the dispatcher, before Spring Security's
     * filter-level handler can see them, so they must be translated here.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action",
                request, List.of());
    }

    /**
     * The client cannot accept JSON, so no error body can be negotiated.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            // Spring MVC framework exceptions (404 no handler, 405, 415, missing parameter, ...) carry their status.
            HttpStatusCode status = errorResponse.getStatusCode();
            log.debug("Client error {} on {} {}: {}", status.value(), request.getMethod(), request.getRequestURI(),
                    ex.getMessage());
            return respond(status, codeFor(status), detailOf(errorResponse, status), request, List.of());
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_ERROR_MESSAGE, request,
                List.of());
    }

    static HttpStatus toHttpStatus(ErrorCategory category) {
        return switch (category) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private ResponseEntity<ApiError> validationFailed(HttpServletRequest request,
                                                      List<ApiError.FieldViolation> violations) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request,
                violations);
    }

    private ResponseEntity<ApiError> respond(HttpStatusCode status, String code, String message,
                                             HttpServletRequest request, List<ApiError.FieldViolation> violations) {
        return ResponseEntity.status(status).body(errorFactory.create(status, code, message, request, violations));
    }

    private static String codeFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.name() : "HTTP_" + status.value();
    }

    private static String detailOf(ErrorResponse errorResponse, HttpStatusCode status) {
        String detail = errorResponse.getBody().getDetail();
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Request failed";
    }

    private static List<ApiError.FieldViolation> concat(List<ApiError.FieldViolation> first,
                                                        List<ApiError.FieldViolation> second) {
        return second.isEmpty() ? first : Stream.concat(first.stream(), second.stream()).toList();
    }
}
