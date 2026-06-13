package com.stockforecast.user.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    UserProfile save(UserProfile profile);

    Optional<UserProfile> findById(UUID userId);

    boolean existsById(UUID userId);
}
