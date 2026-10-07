package com.interviewprep.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "12345678", "correct horse battery", "पासवर्ड१२३४"})
    void acceptsBlankForNotBlankToReportAndPasswordsWithinLimits(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @Test
    void rejectsFewerThan8Characters() {
        assertThat(validator.isValid("1234567", null)).isFalse();
    }

    @Test
    void acceptsExactly72BytesAndRejects73() {
        assertThat(validator.isValid("a".repeat(72), null)).isTrue();
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
    }

    @Test
    void countsBytesNotCharactersForTheUpperLimit() {
        // 25 three-byte characters: 25 characters but 75 bytes, beyond what bcrypt uses.
        assertThat(validator.isValid("€".repeat(25), null)).isFalse();
        assertThat(validator.isValid("€".repeat(24), null)).isTrue();
    }
}
