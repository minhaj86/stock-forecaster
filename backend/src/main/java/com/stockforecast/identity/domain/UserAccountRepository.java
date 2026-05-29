package com.stockforecast.identity.domain;

import com.stockforecast.identity.Role;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link UserAccount}. E-mail arguments must already be normalised
 * (see {@link EmailAddress#value()}).
 */
public interface UserAccountRepository {

    UserAccount save(UserAccount account);

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    long count();

    long countByRole(Role role);

    long countByCreatedAtGreaterThanEqual(Instant since);
}
