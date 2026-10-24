package com.example.iamsystem.security.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Account lockout after failed logins ({@code security.max-failed-attempts}, {@code security.lockout-duration-minutes}).
 */
@Validated
@ConfigurationProperties("security")
public record LockoutProperties(@DefaultValue("5") @Positive int maxFailedAttempts,
                                @DefaultValue("15") @Positive int lockoutDurationMinutes) {
}
