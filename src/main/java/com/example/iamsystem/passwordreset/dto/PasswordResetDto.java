package com.example.iamsystem.passwordreset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_ID_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.IDENTIFIER_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.OTP_REQUIRED;
import static com.example.iamsystem.common.util.SecretMask.MASK;

/**
 * Completes a self-service password reset with the emailed OTP. The client decides the tenant; {@code identifier} is
 * the username or the email.
 */
public record PasswordResetDto(@JsonProperty("client_id") @NotBlank(message = CLIENT_ID_REQUIRED) String clientId,
                               @NotBlank(message = IDENTIFIER_REQUIRED) String identifier,
                               @NotBlank(message = OTP_REQUIRED) String otp) {

    /**
     * Never shows the secret, so logging the request is safe.
     */
    @Override
    public String toString() {
        return "PasswordResetDto[clientId=" + clientId + ", identifier=" + identifier + ", otp=" + MASK + "]";
    }
}
