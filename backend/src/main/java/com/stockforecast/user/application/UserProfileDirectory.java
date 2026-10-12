package com.stockforecast.user.application;

import com.stockforecast.user.UserProfiles;
import com.stockforecast.user.domain.UserProfile;
import com.stockforecast.user.domain.UserProfileNotFoundException;
import com.stockforecast.user.domain.UserProfileRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Profile access for administrators (method security restricts it to the ADMIN role).
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
class UserProfileDirectory implements UserProfiles {

    private final UserProfileRepository profiles;
    private final Clock clock;

    UserProfileDirectory(UserProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileSummary> find(UUID userId) {
        return profiles.findById(userId).map(UserProfileDirectory::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findDisplayNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return profiles.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, UserProfile::getDisplayName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findUserIdsByDisplayName(String fragment, int limit) {
        if (fragment == null || fragment.isBlank() || limit <= 0) {
            return List.of();
        }
        String escaped = fragment.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return profiles.findUserIdsByDisplayNameLike("%" + escaped + "%", limit);
    }

    @Override
    @Transactional
    public ProfileSummary changeDisplayName(UUID userId, String displayName) {
        UserProfile profile = profiles.findById(userId).orElseThrow(UserProfileNotFoundException::new);
        profile.update(displayName, null, clock.instant());
        return summary(profiles.save(profile));
    }

    private static ProfileSummary summary(UserProfile profile) {
        return new ProfileSummary(profile.getUserId(), profile.getDisplayName(), profile.getTimeZone());
    }
}
