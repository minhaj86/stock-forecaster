package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.shared.api.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 responses: RFC 6750 {@code WWW-Authenticate} header plus the standard {@code ApiError} body.
 */
@Component
class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ApiErrorResponseWriter errorWriter;

    ApiAuthenticationEntryPoint(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        bearerEntryPoint.commence(request, response, exception);
        if (exception instanceof OAuth2AuthenticationException) {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN",
                    "The access token is invalid or has expired");
        } else {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Authentication is required to access this resource");
        }
    }
}
