package com.stockforecast.shared.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's whitelabel/basic error endpoint so that container-level errors (for example a
 * failure inside a servlet filter) use the same {@link ApiError} envelope as everything else.
 */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

    private final ApiErrorFactory errorFactory;

    public ApiErrorController(ApiErrorFactory errorFactory) {
        this.errorFactory = errorFactory;
    }

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String message = status.is5xxServerError()
                ? GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE
                : status.getReasonPhrase();
        ApiError body = errorFactory.create(status, status.is5xxServerError() ? "INTERNAL_ERROR" : status.name(),
                message, request);
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalUri instanceof String uri) {
            body = new ApiError(body.timestamp(), body.status(), body.code(), body.message(), uri,
                    body.correlationId(), body.errors(), body.details());
        }
        return ResponseEntity.status(status).body(body);
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer value) {
            HttpStatus status = HttpStatus.resolve(value);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
