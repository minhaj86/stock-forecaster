package com.stockforecast.identity.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void normalisesCaseAndSurroundingWhitespace() {
        assertThat(EmailAddress.of("  Ada.Lovelace@Example.COM ").value()).isEqualTo("ada.lovelace@example.com");
    }

    @Test
    void equalAddressesDifferingOnlyInCaseAreEqual() {
        assertThat(EmailAddress.of("ADA@example.com")).isEqualTo(EmailAddress.of("ada@EXAMPLE.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ada", "ada@", "@example.com", "ada@example", "ada@@example.com",
            "ada lovelace@example.com", "ada@example..com", "ada@.example.com"})
    void rejectsMalformedAddresses(String raw) {
        assertThatThrownBy(() -> EmailAddress.of(raw))
                .isInstanceOf(InvalidEmailAddressException.class)
                .extracting("code").isEqualTo("INVALID_EMAIL");
        assertThat(EmailAddress.tryParse(raw)).isEmpty();
    }

    @Test
    void rejectsAddressesLongerThan320Characters() {
        String tooLong = "a".repeat(309) + "@example.com";

        assertThat(tooLong).hasSize(321);
        assertThat(EmailAddress.tryParse(tooLong)).isEmpty();
    }

    @Test
    void tryParseReturnsNormalisedAddress() {
        assertThat(EmailAddress.tryParse("Ada@Example.com")).contains(EmailAddress.of("ada@example.com"));
    }
}
