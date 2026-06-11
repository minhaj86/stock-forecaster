package com.stockforecast.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = PasswordPolicy.DEFAULT;

    @Test
    void acceptsPasswordsWithinBounds() {
        assertThatCode(() -> policy.validate("twelve-chars")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("x".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortBlankAndMissingPasswords() {
        assertThatThrownBy(() -> policy.validate("eleven-char")).isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> policy.validate(" ".repeat(20))).isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void limitsUtf8BytesNotCharactersBecauseBcryptTruncatesAt72Bytes() {
        String twentyFiveEuroSigns = "€".repeat(25); // 25 characters, 75 UTF-8 bytes

        assertThat(policy.exceedsMaximumLength(twentyFiveEuroSigns)).isTrue();
        assertThatThrownBy(() -> policy.validate(twentyFiveEuroSigns))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .extracting("code").isEqualTo("PASSWORD_POLICY_VIOLATION");
    }

    @Test
    void countsCodePointsForTheMinimumLength() {
        String twelveEmoji = "🔒".repeat(12); // 12 code points, 24 UTF-16 chars, 48 bytes

        assertThatCode(() -> policy.validate(twelveEmoji)).doesNotThrowAnyException();
    }
}
