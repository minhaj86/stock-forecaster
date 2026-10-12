package com.stockforecast.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ChangePasswordRequest(
        @NotBlank @Size(max = 128) @Schema(description = "The current or temporary password") String currentPassword,
        @NotBlank @Size(min = 12, max = 72) @Schema(description = "12-72 characters (at most 72 UTF-8 bytes)")
        String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
