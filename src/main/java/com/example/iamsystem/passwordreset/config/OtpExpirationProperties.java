package com.example.iamsystem.passwordreset.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How long a password-reset OTP stays valid ({@code password.reset.otp.expiration.minutes}).
 */
@Validated
@ConfigurationProperties("password.reset.otp.expiration")
public record OtpExpirationProperties(@Positive int minutes) {
}
