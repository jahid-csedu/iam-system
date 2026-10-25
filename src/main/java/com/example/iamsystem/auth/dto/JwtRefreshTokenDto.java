package com.example.iamsystem.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_ID_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.REFRESH_TOKEN_REQUIRED;
import static com.example.iamsystem.common.util.SecretMask.MASK;

/**
 * A refresh token and the client it was issued through.
 */
public record JwtRefreshTokenDto(@JsonProperty("client_id") @NotBlank(message = CLIENT_ID_REQUIRED) String clientId,
                                 @JsonProperty("refresh_token") @NotBlank(message = REFRESH_TOKEN_REQUIRED) String refreshToken) {

    /**
     * Never shows the secret, so logging the request is safe.
     */
    @Override
    public String toString() {
        return "JwtRefreshTokenDto[clientId=" + clientId + ", refreshToken=" + MASK + "]";
    }
}
