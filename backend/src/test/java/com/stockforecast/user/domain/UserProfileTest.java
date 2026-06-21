package com.stockforecast.user.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-11T12:00:00Z");

    @Test
    void newProfilesHaveATrimmedDisplayNameAndUtcTimeZone() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "  Ada Lovelace ", CREATED);

        assertThat(profile.getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(profile.getTimeZone()).isEqualTo("UTC");
        assertThat(profile.getCreatedAt()).isEqualTo(CREATED);
        assertThat(profile.getUpdatedAt()).isEqualTo(CREATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "line\nbreak", "tab\tbed"})
    void rejectsBlankOrControlCharacterDisplayNames(String displayName) {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), displayName, CREATED))
                .isInstanceOf(InvalidProfileException.class)
                .extracting("code").isEqualTo("INVALID_DISPLAY_NAME");
    }

    @Test
    void rejectsDisplayNamesLongerThan100Characters() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), "x".repeat(101), CREATED))
                .isInstanceOf(InvalidProfileException.class);
    }

    @Test
    void partialUpdateChangesOnlyProvidedAttributes() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "Ada", CREATED);

        profile.update(null, "Europe/Berlin", LATER);

        assertThat(profile.getDisplayName()).isEqualTo("Ada");
        assertThat(profile.getTimeZone()).isEqualTo("Europe/Berlin");
        assertThat(profile.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectsUnknownTimeZones() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "Ada", CREATED);

        assertThatThrownBy(() -> profile.update(null, "Mars/Olympus_Mons", LATER))
                .isInstanceOf(InvalidProfileException.class)
                .extracting("code").isEqualTo("INVALID_TIME_ZONE");
        assertThat(profile.getTimeZone()).isEqualTo("UTC");
    }

    @Test
    void noOpUpdatesDoNotTouchTheModificationTimestamp() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "Ada", CREATED);

        profile.update(" Ada ", "UTC", LATER);

        assertThat(profile.getUpdatedAt()).isEqualTo(CREATED);
    }
}
