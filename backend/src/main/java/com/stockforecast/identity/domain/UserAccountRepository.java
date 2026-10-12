package com.stockforecast.identity.domain;

import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.Role;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link UserAccount}. E-mail arguments must already be normalised
 * (see {@link EmailAddress#value()}).
 */
public interface UserAccountRepository {

    UserAccount save(UserAccount account);

    void delete(UserAccount account);

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findAllByIdIn(Collection<UUID> ids);

    boolean existsByEmail(String email);

    long count();

    long countByRole(Role role);

    long countByStatus(AccountStatus status);

    long countByCreatedAtGreaterThanEqual(Instant since);

    /**
     * Locks every active administrator row ({@code SELECT … FOR UPDATE}) so concurrent demotions or deletions of
     * administrators are serialised and the "at least one active administrator" rule cannot be raced.
     */
    List<UserAccount> lockActiveAdministrators();
}
