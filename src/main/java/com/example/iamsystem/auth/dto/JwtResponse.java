package com.example.iamsystem.auth.dto;

import com.example.iamsystem.common.util.SecretMask;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The tokens of a login or refresh, shaped like an OAuth2 token response. {@code expires_in} is the access token's
 * lifetime in seconds; {@code refresh_token} is left out when the client doesn't issue refresh tokens.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JwtResponse(@JsonProperty("access_token") String accessToken,
                          @JsonProperty("token_type") String tokenType,
                          @JsonProperty("expires_in") long expiresIn,
                          @JsonProperty("refresh_token") String refreshToken,
                          String username) {

    public static final String BEARER = "Bearer";

    public JwtResponse(String accessToken, long expiresIn, String refreshToken, String username) {
        this(accessToken, BEARER, expiresIn, refreshToken, username);
    }

    @Override
    public String toString() {
        return "JwtResponse[accessToken=" + SecretMask.MASK + ", tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", refreshToken=" + (refreshToken == null ? null : SecretMask.MASK) + ", username=" + username + "]";
    }
}
