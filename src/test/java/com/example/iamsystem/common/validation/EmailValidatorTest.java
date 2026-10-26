package com.example.iamsystem.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailValidatorTest {

    private final EmailValidator validator = new EmailValidator();

    @Test
    @DisplayName("Ordinary addresses are valid, with dots, plus signs, hyphens and subdomains")
    void ordinaryAddresses_areValid() {
        List<String> valid = List.of("bob@example.com", "first.last@example.co.uk", "bob+iam@example.com",
                "b-o_b@my-host.example-mail.org", "x@sub.domain.io");

        for (String email : valid) {
            assertTrue(validator.isValid(email, null), email);
        }
    }

    @Test
    @DisplayName("Malformed addresses are invalid, including a missing dot before the top-level domain")
    void malformedAddresses_areInvalid() {
        List<String> invalid = List.of("bob", "bob@", "@example.com", "bob@examplecom", "bob@example.c", "bob@@example.com",
                "bob @example.com", "bob@example..com", "");

        for (String email : invalid) {
            assertFalse(validator.isValid(email, null), email);
        }
    }

    @Test
    @DisplayName("A missing email is left to @NotNull")
    void null_isValid() {
        assertTrue(validator.isValid(null, null));
    }
}
