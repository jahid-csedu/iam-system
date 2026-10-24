package com.example.iamsystem.client.dto;

import com.example.iamsystem.client.entity.TokenClaim;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.ACCESS_TOKEN_TTL_RANGE;
import static com.example.iamsystem.common.exception.ErrorMessage.REFRESH_TOKEN_TTL_RANGE;
import static com.example.iamsystem.common.exception.ErrorMessage.REFRESH_TOKEN_TTL_TOO_SHORT;
import static com.example.iamsystem.common.exception.ErrorMessage.TOKEN_CLAIMS_REQUIRED;

/**
 * How the tokens issued through a client look. Access tokens live 1 minute to 1 hour; refresh tokens 1 hour to 30 days
 * and always longer than access tokens.
 */
public record TokenSettingsDto(
        @Min(value = MIN_ACCESS_TOKEN_TTL_SECONDS, message = ACCESS_TOKEN_TTL_RANGE)
        @Max(value = MAX_ACCESS_TOKEN_TTL_SECONDS, message = ACCESS_TOKEN_TTL_RANGE)
        int accessTokenTtlSeconds,

        boolean refreshTokenEnabled,

        @Min(value = MIN_REFRESH_TOKEN_TTL_SECONDS, message = REFRESH_TOKEN_TTL_RANGE)
        @Max(value = MAX_REFRESH_TOKEN_TTL_SECONDS, message = REFRESH_TOKEN_TTL_RANGE)
        int refreshTokenTtlSeconds,

        @NotNull(message = TOKEN_CLAIMS_REQUIRED)
        Set<TokenClaim> claims
) {

    public static final int MIN_ACCESS_TOKEN_TTL_SECONDS = 60;
    public static final int MAX_ACCESS_TOKEN_TTL_SECONDS = 3600;
    public static final int MIN_REFRESH_TOKEN_TTL_SECONDS = 3600;
    public static final int MAX_REFRESH_TOKEN_TTL_SECONDS = 30 * 24 * 3600;

    @JsonIgnore
    @AssertTrue(message = REFRESH_TOKEN_TTL_TOO_SHORT)
    public boolean isRefreshTokenOutlivingAccessToken() {
        return refreshTokenTtlSeconds > accessTokenTtlSeconds;
    }
}
