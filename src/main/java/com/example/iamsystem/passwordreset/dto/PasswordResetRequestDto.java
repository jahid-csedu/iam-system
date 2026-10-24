package com.example.iamsystem.passwordreset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_ID_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.IDENTIFIER_REQUIRED;

/**
 * Starts a self-service password reset. The client decides the tenant; {@code identifier} is the username or the email.
 */
public record PasswordResetRequestDto(@JsonProperty("client_id") @NotBlank(message = CLIENT_ID_REQUIRED) String clientId,
                                      @NotBlank(message = IDENTIFIER_REQUIRED) String identifier) {
}
