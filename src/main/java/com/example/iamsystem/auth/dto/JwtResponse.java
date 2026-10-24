package com.example.iamsystem.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The tokens of a login or refresh. {@code refresh_token} is left out when the client doesn't issue refresh tokens.
 */
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JwtResponse {
    private final String username;
    @JsonProperty("refresh_token")
    private final String refreshToken;
    @JsonProperty("access_token")
    private final String accessToken;
}
