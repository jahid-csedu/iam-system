package com.example.iamsystem.auth.dto;

import com.example.iamsystem.security.jwt.SigningKey;

import java.util.List;

/**
 * The JSON Web Key Set other applications verify tokens with.
 */
public record JwkSetDto(List<JwkDto> keys) {

    public static JwkSetDto of(List<SigningKey> signingKeys) {
        return new JwkSetDto(signingKeys.stream().map(JwkDto::of).toList());
    }
}
