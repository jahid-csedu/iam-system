package com.example.iamsystem.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class JwtConstant {
    public static final String REQUEST_HEADER = "Authorization";
    public static final String ACCESS_TOKEN_SECRET_KEY = "dGhpc0lzTXlEZWZhdWx0U2VjdXJpdHlKd3RTZWNyZXQ=";
    public static final String REFRESH_TOKEN_SECRET = "dGhpc0lzTXhdWx0UlEZWZhdWx0dfdU2VjdXJpdHlKd3hdWx0URTZWNyZXQ=";
    public static final long JWT_ACCESS_TOKEN_VALIDITY = 5L * 60; // 5 minutes
    public static final long JWT_REFRESH_TOKEN_VALIDITY = 24L * 60 * 60; // 24 hours
    public static final String BEARER = "Bearer ";
    public static final String UNAUTHORIZED_RESPONSE = "Unauthorized";
}
