package com.example.iamsystem.user.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How long a password stays valid after it is set ({@code password.expiration.days}).
 */
@Validated
@ConfigurationProperties("password.expiration")
public record PasswordExpirationProperties(@Positive int days) {
}
