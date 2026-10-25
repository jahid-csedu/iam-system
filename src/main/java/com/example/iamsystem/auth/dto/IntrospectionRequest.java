package com.example.iamsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.TOKEN_REQUIRED;
import static com.example.iamsystem.common.util.SecretMask.MASK;

public record IntrospectionRequest(@NotBlank(message = TOKEN_REQUIRED) String token) {

    /**
     * Never shows the secret, so logging the request is safe.
     */
    @Override
    public String toString() {
        return "IntrospectionRequest[token=" + MASK + "]";
    }
}
