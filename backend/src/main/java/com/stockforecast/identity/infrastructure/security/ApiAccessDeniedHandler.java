package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.shared.api.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter errorWriter;

    ApiAccessDeniedHandler(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (AccessPolicies.isPasswordChangePending(SecurityContextHolder.getContext().getAuthentication())) {
            errorWriter.write(request, response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "You must choose a new password before continuing");
            return;
        }
        errorWriter.write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action");
    }
}
