package com.stockforecast.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Writes {@link ApiError} bodies outside of Spring MVC (security filters, entry points), where
 * {@code @RestControllerAdvice} does not apply.
 */
@Component
public class ApiErrorResponseWriter {

    private final ApiErrorFactory errorFactory;
    private final JsonMapper jsonMapper;

    public ApiErrorResponseWriter(ApiErrorFactory errorFactory, JsonMapper jsonMapper) {
        this.errorFactory = errorFactory;
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                      String message) throws IOException {
        ApiError error = errorFactory.create(status, code, message, request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), error);
    }
}
