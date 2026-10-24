package com.example.iamsystem.passwordreset.dto;

import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.IDENTIFIER_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.OTP_REQUIRED;

/**
 * Completes a self-service password reset with the emailed OTP. {@code identifier} is the username or the email address.
 */
public record PasswordResetDto(@NotBlank(message = IDENTIFIER_REQUIRED) String identifier,
                               @NotBlank(message = OTP_REQUIRED) String otp) {
}
