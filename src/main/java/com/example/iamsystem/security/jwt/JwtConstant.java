package com.example.iamsystem.security.jwt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JwtConstant {
    public static final String REQUEST_HEADER = "Authorization";
    public static final String ACCESS_TOKEN_SECRET_KEY = "dGhpc0lzTXlEZWZhdWx0U2VjdXJpdHlKd3RTZWNyZXQ=";
    public static final String REFRESH_TOKEN_SECRET = "dGhpc0lzTXhdWx0UlEZWZhdWx0dfdU2VjdXJpdHlKd3hdWx0URTZWNyZXQ=";
    public static final String BEARER = "Bearer ";
    public static final String UNAUTHORIZED_RESPONSE = "Unauthorized";
}
