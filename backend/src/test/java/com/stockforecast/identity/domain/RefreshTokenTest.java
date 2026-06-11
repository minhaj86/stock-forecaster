package com.stockforecast.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);

    @Test
    void newSessionTokenIsActiveUntilItsSlidingExpiry() {
        RefreshToken token = RefreshToken.startSession(USER_ID, "hash-1", NOW, TTL, SESSION_LIFETIME, true);

        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(token.getSessionExpiresAt()).isEqualTo(NOW.plus(SESSION_LIFETIME));
        assertThat(token.isActive(NOW.plus(TTL).minusSeconds(1))).isTrue();
        assertThat(token.isActive(NOW.plus(TTL))).isFalse();
    }

    @Test
    void rotationRevokesTheTokenAndIssuesASuccessorInTheSameFamily() {
        RefreshToken original = RefreshToken.startSession(USER_ID, "hash-1", NOW, TTL, SESSION_LIFETIME, true);
        Instant later = NOW.plus(Duration.ofDays(1));

        RefreshToken successor = original.rotate("hash-2", later, TTL);

        assertThat(original.isRevoked()).isTrue();
        assertThat(original.getRevocationReason()).isEqualTo(RevocationReason.ROTATED);
        assertThat(original.getRevokedAt()).isEqualTo(later);
        assertThat(successor.getFamilyId()).isEqualTo(original.getFamilyId());
        assertThat(successor.getUserId()).isEqualTo(USER_ID);
        assertThat(successor.getId()).isNotEqualTo(original.getId());
        assertThat(successor.isActive(later)).isTrue();
        assertThat(successor.getExpiresAt()).isEqualTo(later.plus(TTL));
    }

    @Test
    void slidingRenewalNeverExtendsBeyondTheAbsoluteSessionLifetime() {
        RefreshToken current = RefreshToken.startSession(USER_ID, "hash-0", NOW, TTL, SESSION_LIFETIME, true);
        for (int day = 6; day <= 24; day += 6) {
            current = current.rotate("hash-" + day, NOW.plus(Duration.ofDays(day)), TTL);
        }

        RefreshToken last = current.rotate("hash-28", NOW.plus(Duration.ofDays(28)), TTL);

        assertThat(last.getExpiresAt()).isEqualTo(NOW.plus(SESSION_LIFETIME));
        assertThat(last.isActive(NOW.plus(SESSION_LIFETIME))).isFalse();
    }

    @Test
    void onlyActiveTokensCanBeRotated() {
        RefreshToken token = RefreshToken.startSession(USER_ID, "hash-1", NOW, TTL, SESSION_LIFETIME, true);
        token.revoke(RevocationReason.LOGOUT, NOW);

        assertThatThrownBy(() -> token.rotate("hash-2", NOW, TTL)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RefreshToken.startSession(USER_ID, "hash-3", NOW, TTL, SESSION_LIFETIME, true)
                .rotate("hash-4", NOW.plus(TTL), TTL)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revocationIsIdempotentAndKeepsTheFirstReason() {
        RefreshToken token = RefreshToken.startSession(USER_ID, "hash-1", NOW, TTL, SESSION_LIFETIME, true);

        token.revoke(RevocationReason.LOGOUT, NOW);
        token.revoke(RevocationReason.REUSE_DETECTED, NOW.plusSeconds(5));

        assertThat(token.getRevocationReason()).isEqualTo(RevocationReason.LOGOUT);
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void detectsReplayWithinTheGracePeriodOnlyForRotatedTokens() {
        RefreshToken rotated = RefreshToken.startSession(USER_ID, "hash-1", NOW, TTL, SESSION_LIFETIME, true);
        rotated.rotate("hash-2", NOW, TTL);
        RefreshToken loggedOut = RefreshToken.startSession(USER_ID, "hash-3", NOW, TTL, SESSION_LIFETIME, true);
        loggedOut.revoke(RevocationReason.LOGOUT, NOW);

        assertThat(rotated.wasRotatedWithin(Duration.ofSeconds(10), NOW.plusSeconds(10))).isTrue();
        assertThat(rotated.wasRotatedWithin(Duration.ofSeconds(10), NOW.plusSeconds(11))).isFalse();
        assertThat(loggedOut.wasRotatedWithin(Duration.ofSeconds(10), NOW.plusSeconds(1))).isFalse();
    }

    @Test
    void rejectsNonPositiveDurations() {
        assertThatThrownBy(() -> RefreshToken.startSession(USER_ID, "hash", NOW, Duration.ZERO, SESSION_LIFETIME, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.startSession(USER_ID, "hash", NOW, TTL, Duration.ofSeconds(-1), true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
