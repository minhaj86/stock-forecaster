package com.stockforecast.user.api;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.Role;
import com.stockforecast.user.application.UserProfileView;

import java.time.Instant;
import java.util.UUID;

record UserProfileResponse(UUID id, String email, Role role, String displayName, String timeZone,
                           Instant memberSince, Instant updatedAt) {

    static UserProfileResponse from(AuthenticatedUser user, UserProfileView profile) {
        return new UserProfileResponse(user.id(), user.email(), user.role(), profile.displayName(),
                profile.timeZone(), profile.createdAt(), profile.updatedAt());
    }
}
