package com.stockforecast.user.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    UserProfile save(UserProfile profile);

    void delete(UserProfile profile);

    Optional<UserProfile> findById(UUID userId);

    List<UserProfile> findAllByUserIdIn(Collection<UUID> userIds);

    boolean existsById(UUID userId);

    /**
     * @param lowerCasePattern a lower-case {@code LIKE} pattern with backslash as the escape character
     */
    List<UUID> findUserIdsByDisplayNameLike(String lowerCasePattern, int limit);
}
