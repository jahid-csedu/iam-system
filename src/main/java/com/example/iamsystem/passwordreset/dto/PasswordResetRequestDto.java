package com.example.iamsystem.passwordreset.dto;

import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.IDENTIFIER_REQUIRED;

/**
 * Starts a self-service password reset. {@code identifier} is the username or the email address.
 */
public record PasswordResetRequestDto(@NotBlank(message = IDENTIFIER_REQUIRED) String identifier) {
}
