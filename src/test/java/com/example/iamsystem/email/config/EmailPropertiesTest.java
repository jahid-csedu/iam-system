package com.example.iamsystem.email.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailPropertiesTest {

    @Test
    @DisplayName("The sender is an address, optionally with a name")
    void validSenders_areAccepted() {
        assertTrue(new EmailProperties("no-reply@example.com").isValidFrom());
        assertTrue(new EmailProperties("IAM System <no-reply@example.com>").isValidFrom());
    }

    @Test
    @DisplayName("A sender that isn't an email address is refused")
    void invalidSenders_areRefused() {
        assertFalse(new EmailProperties("no-reply").isValidFrom());
        assertFalse(new EmailProperties("IAM System <no-reply@example.com").isValidFrom());
        assertFalse(new EmailProperties("a@b.com, c@d.com").isValidFrom());
    }
}
