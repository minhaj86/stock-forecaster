package com.stockforecast.identity.api;

import com.stockforecast.identity.application.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
        @NotBlank @Size(max = 320) @Schema(example = "ada@example.com") String email,
        @NotBlank @Size(max = 128) String password,
        @Schema(description = "Keep the session after the browser is closed (defaults to true)") Boolean rememberMe) {

    LoginRequest {
        email = email == null ? null : email.strip();
    }

    LoginCommand toCommand() {
        return new LoginCommand(email, password, rememberMe == null || rememberMe);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***, rememberMe=" + rememberMe + "]";
    }
}
