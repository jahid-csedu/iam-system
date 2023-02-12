package com.example.management.constant;

public class JwtConstant {
    public static final String REQUEST_HEADER = "Authorization";
    public static final String ACCESS_TOKEN_SECRET_KEY = "dGhpc0lzTXlEZWZhdWx0U2VjdXJpdHlKd3RTZWNyZXQ=";
    public static final String REFRESH_TOKEN_SECRET = "dGhpc0lzTXhdWx0UlEZWZhdWx0dfdU2VjdXJpdHlKd3hdWx0URTZWNyZXQ=";
    public static final long JWT_ACCESS_TOKEN_VALIDITY = 5*60; // 5 minutes
    public static final long JWT_REFRESH_TOKEN_VALIDITY = 24*60*60; // 24 hours
    public static final String BEARER = "Bearer ";
    public static final String UNTHORIZED_RESPONSE = "Unauthorized";
}
