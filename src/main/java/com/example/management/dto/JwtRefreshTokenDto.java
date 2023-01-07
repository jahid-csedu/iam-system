package com.example.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JwtRefreshTokenDto {
    private final String username;
    @JsonProperty("refresh_token")
    private final String refreshToken;
}
