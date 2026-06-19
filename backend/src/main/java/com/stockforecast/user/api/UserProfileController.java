package com.stockforecast.user.api;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.CurrentUser;
import com.stockforecast.user.application.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Me" resource: the user is always derived from the access token, so no endpoint accepts a user id.
 */
@Tag(name = "User profile")
@RestController
@RequestMapping("/api/v1/users/me")
class UserProfileController {

    private final UserProfileService profileService;

    UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Get the signed-in user's profile")
    @GetMapping
    UserProfileResponse get(@CurrentUser AuthenticatedUser user) {
        return UserProfileResponse.from(user, profileService.getProfile(user.id()));
    }

    @Operation(summary = "Update the signed-in user's profile")
    @PatchMapping
    UserProfileResponse update(@CurrentUser AuthenticatedUser user,
                               @Valid @RequestBody UpdateUserProfileRequest request) {
        return UserProfileResponse.from(user, profileService.updateProfile(user.id(), request.toCommand()));
    }
}
