package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.Identifiers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One link in a login session's chain of single-use refresh tokens.
 *
 * <p>Only a SHA-256 hash of the opaque token is stored. Every refresh rotates the token; all tokens of a login
 * share a {@code familyId} so that replay of a rotated token can revoke the entire session. Each token's
 * expiry is capped by the session's absolute lifetime, so sliding renewal cannot extend a session forever.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "session_expires_at", nullable = false, updatable = false)
    private Instant sessionExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 32)
    private RevocationReason revocationReason;

    /** "Remember me": whether the client may keep the token beyond the browser session. */
    @Column(nullable = false, updatable = false)
    private boolean persistent;

    /** Optimistic lock; a null version also tells Spring Data that a freshly created token is new. */
    @Version
    private Long version;

    protected RefreshToken() {
        // required by JPA
    }

    private RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant issuedAt, Duration ttl,
                         Instant sessionExpiresAt, boolean persistent) {
        this.id = Identifiers.newId();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.sessionExpiresAt = Objects.requireNonNull(sessionExpiresAt, "sessionExpiresAt");
        this.persistent = persistent;
        Instant slidingExpiry = issuedAt.plus(requirePositive(ttl));
        this.expiresAt = slidingExpiry.isBefore(sessionExpiresAt) ? slidingExpiry : sessionExpiresAt;
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("refresh token would already be expired");
        }
    }

    public static RefreshToken startSession(UUID userId, String tokenHash, Instant now, Duration ttl,
                                            Duration sessionLifetime, boolean persistent) {
        return new RefreshToken(userId, Identifiers.newId(), tokenHash, now, ttl,
                now.plus(requirePositive(sessionLifetime)), persistent);
    }

    /**
     * Revokes this token and returns its successor in the same session.
     */
    public RefreshToken rotate(String successorTokenHash, Instant now, Duration ttl) {
        if (!isActive(now)) {
            throw new IllegalStateException("only an active refresh token can be rotated");
        }
        revoke(RevocationReason.ROTATED, now);
        return new RefreshToken(userId, familyId, successorTokenHash, now, ttl, sessionExpiresAt, persistent);
    }

    /**
     * Idempotent: the first revocation reason wins.
     */
    public void revoke(RevocationReason reason, Instant now) {
        if (revokedAt == null) {
            this.revokedAt = Objects.requireNonNull(now, "now");
            this.revocationReason = Objects.requireNonNull(reason, "reason");
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * A rotated token replayed shortly after rotation is most likely a benign race (two browser tabs refreshing
     * at once) rather than theft.
     */
    public boolean wasRotatedWithin(Duration gracePeriod, Instant now) {
        return revocationReason == RevocationReason.ROTATED && !now.isAfter(revokedAt.plus(gracePeriod));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSessionExpiresAt() {
        return sessionExpiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RevocationReason getRevocationReason() {
        return revocationReason;
    }

    public boolean isPersistent() {
        return persistent;
    }

    private static Duration requirePositive(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        return duration;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RefreshToken token && id.equals(token.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", familyId=" + familyId + ", revocationReason=" + revocationReason + "]";
    }
}
