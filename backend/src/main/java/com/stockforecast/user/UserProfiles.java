package com.stockforecast.user;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Published profile queries and administrative changes (used by the admin console). Regular users manage their
 * own profile through {@code /api/v1/users/me} only.
 */
public interface UserProfiles {

    Optional<ProfileSummary> find(UUID userId);

    Map<UUID, String> findDisplayNames(Collection<UUID> userIds);

    /**
     * Users whose display name contains the fragment (case-insensitive), at most {@code limit} of them.
     */
    List<UUID> findUserIdsByDisplayName(String fragment, int limit);

    ProfileSummary changeDisplayName(UUID userId, String displayName);

    record ProfileSummary(UUID userId, String displayName, String timeZone) {
    }
}
