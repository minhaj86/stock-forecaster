package com.stockforecast.identity.api;

import com.stockforecast.identity.application.RegisterUserCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        @NotBlank @Email @Size(max = 320) @Schema(example = "ada@example.com") String email,
        @NotBlank @Size(min = 12, max = 72) @Schema(description = "12-72 characters (at most 72 UTF-8 bytes)")
        String password,
        @NotBlank @Size(max = 100) @Schema(example = "Ada Lovelace") String displayName) {

    /** Trims before validation so pasted values with stray whitespace are accepted consistently with login. */
    RegisterRequest {
        email = email == null ? null : email.strip();
        displayName = displayName == null ? null : displayName.strip();
    }

    RegisterUserCommand toCommand() {
        return new RegisterUserCommand(email, password, displayName);
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
