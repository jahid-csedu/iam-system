package com.example.iamsystem.email.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The sender of every email ({@code iam.mail.from}): an address, optionally with a name, e.g.
 * {@code IAM System <no-reply@example.com>}. Many mail servers only accept the account's own address or a verified alias.
 * An invalid one stops the startup.
 */
@Validated
@ConfigurationProperties("iam.mail")
public record EmailProperties(@NotBlank String from) {

    @AssertTrue(message = "must be an email address, optionally with a name: Name <address@example.com>")
    public boolean isValidFrom() {
        if (from == null || from.isBlank()) {
            return true;
        }
        try {
            new InternetAddress(from, true).validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }
}
