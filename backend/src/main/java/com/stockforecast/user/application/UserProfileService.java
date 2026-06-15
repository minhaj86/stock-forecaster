package com.stockforecast.user.application;

import com.stockforecast.user.domain.UserProfileNotFoundException;
import com.stockforecast.user.domain.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Profile use cases. Callers pass the authenticated user's id; there is deliberately no way to address another
 * user's profile.
 */
@Service
public class UserProfileService {

    private final UserProfileRepository profiles;
    private final Clock clock;

    public UserProfileService(UserProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfileView getProfile(UUID userId) {
        return profiles.findById(userId).map(UserProfileView::from).orElseThrow(UserProfileNotFoundException::new);
    }

    @Transactional
    public UserProfileView updateProfile(UUID userId, UpdateUserProfileCommand command) {
        var profile = profiles.findById(userId).orElseThrow(UserProfileNotFoundException::new);
        profile.update(command.displayName(), command.timeZone(), clock.instant());
        return UserProfileView.from(profiles.save(profile));
    }
}
