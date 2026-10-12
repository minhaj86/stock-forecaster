package com.stockforecast.user.infrastructure.persistence;

import com.stockforecast.user.domain.UserProfile;
import com.stockforecast.user.domain.UserProfileRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

interface SpringDataUserProfileRepository extends Repository<UserProfile, UUID>, UserProfileRepository {

    @Override
    @Query(value = """
            SELECT p.user_id FROM user_profiles p
             WHERE lower(p.display_name) LIKE :lowerCasePattern ESCAPE '\\'
             ORDER BY p.user_id
             LIMIT :limit""", nativeQuery = true)
    List<UUID> findUserIdsByDisplayNameLike(String lowerCasePattern, int limit);
}
