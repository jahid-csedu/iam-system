package com.example.iamsystem.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Getter
@RequiredArgsConstructor
public class JwtResponse implements Serializable {
    private final String username;
    @JsonProperty("refresh_token")
    private final String refreshToken;
    @JsonProperty("access_token")
    private final String accessToken;
}
