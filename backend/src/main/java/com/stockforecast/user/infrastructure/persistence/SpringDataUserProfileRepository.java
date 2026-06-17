package com.stockforecast.user.infrastructure.persistence;

import com.stockforecast.user.domain.UserProfile;
import com.stockforecast.user.domain.UserProfileRepository;
import org.springframework.data.repository.Repository;

import java.util.UUID;

interface SpringDataUserProfileRepository extends Repository<UserProfile, UUID>, UserProfileRepository {
}
