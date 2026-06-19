package com.stockforecast.user.api;

import com.stockforecast.user.application.UpdateUserProfileCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial update; omitted fields are left unchanged")
record UpdateUserProfileRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        @Schema(example = "Ada L.") String displayName,
        @Size(min = 1, max = 64) @Schema(example = "Europe/Berlin") String timeZone) {

    UpdateUserProfileCommand toCommand() {
        return new UpdateUserProfileCommand(displayName, timeZone);
    }
}
