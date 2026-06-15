package com.stockforecast.user.application;

import com.stockforecast.user.domain.UserProfile;

import java.time.Instant;
import java.util.UUID;

public record UserProfileView(UUID userId, String displayName, String timeZone, Instant createdAt,
                              Instant updatedAt) {

    static UserProfileView from(UserProfile profile) {
        return new UserProfileView(profile.getUserId(), profile.getDisplayName(), profile.getTimeZone(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
