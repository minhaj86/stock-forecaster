package com.stockforecast.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link RefreshToken}.
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    /**
     * Loads the token with a row lock so concurrent refreshes of the same token are serialised.
     */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /**
     * Revokes every still-active token of a login session.
     *
     * @return number of tokens revoked
     */
    int revokeFamily(UUID familyId, RevocationReason reason, Instant revokedAt);
}
