package com.stockforecast.identity.infrastructure.persistence;

import com.stockforecast.identity.domain.RefreshToken;
import com.stockforecast.identity.domain.RefreshTokenRepository;
import com.stockforecast.identity.domain.RevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRefreshTokenRepository extends Repository<RefreshToken, UUID>, RefreshTokenRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt, t.revocationReason = :reason, t.version = t.version + 1
             where t.familyId = :familyId and t.revokedAt is null""")
    int revokeFamily(UUID familyId, RevocationReason reason, Instant revokedAt);
}
