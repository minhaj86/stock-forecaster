package com.stockforecast.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's profile, keyed by the identity module's user id (one-to-one, no shared entity).
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    public static final int DISPLAY_NAME_MAX_LENGTH = 100;
    public static final String DEFAULT_TIME_ZONE = "UTC";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = DISPLAY_NAME_MAX_LENGTH)
    private String displayName;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected UserProfile() {
        // required by JPA
    }

    private UserProfile(UUID userId, String displayName, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.displayName = validDisplayName(displayName);
        this.timeZone = DEFAULT_TIME_ZONE;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static UserProfile create(UUID userId, String displayName, Instant now) {
        return new UserProfile(userId, displayName, now);
    }

    /**
     * Partial update: {@code null} arguments leave the corresponding attribute unchanged.
     */
    public void update(String newDisplayName, String newTimeZone, Instant now) {
        String displayNameToApply = newDisplayName == null ? displayName : validDisplayName(newDisplayName);
        String timeZoneToApply = newTimeZone == null ? timeZone : validTimeZone(newTimeZone);
        if (!displayNameToApply.equals(displayName) || !timeZoneToApply.equals(timeZone)) {
            this.displayName = displayNameToApply;
            this.timeZone = timeZoneToApply;
            this.updatedAt = Objects.requireNonNull(now, "now");
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String validDisplayName(String value) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > DISPLAY_NAME_MAX_LENGTH
                || stripped.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidProfileException("INVALID_DISPLAY_NAME",
                    "Display name must be 1-%d printable characters".formatted(DISPLAY_NAME_MAX_LENGTH));
        }
        return stripped;
    }

    private static String validTimeZone(String value) {
        try {
            return ZoneId.of(value.strip()).getId();
        } catch (DateTimeException e) {
            throw new InvalidProfileException("INVALID_TIME_ZONE",
                    "Time zone must be a valid IANA identifier such as Europe/Berlin");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UserProfile profile && userId.equals(profile.getUserId());
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    @Override
    public String toString() {
        return "UserProfile[userId=" + userId + "]";
    }
}
