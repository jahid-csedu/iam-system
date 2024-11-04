package com.example.iamsystem.dto;

import com.example.iamsystem.constant.ErrorMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JwtRefreshTokenDto {
    @NotBlank(message = ErrorMessage.USERNAME_REQUIRED)
    private final String username;

    @NotBlank(message = ErrorMessage.REFRESH_TOKEN_REQUIRED)
    @JsonProperty("refresh_token")
    private final String refreshToken;
}
