package com.example.iamsystem.security.jwt;

/**
 * The two kinds of token, told apart by the {@code typ} header: an access token is never accepted as a refresh token,
 * nor the other way round. {@code at+jwt} is the JWT access-token type of RFC 9068.
 */
public enum TokenType {
    ACCESS_TOKEN("at+jwt"),
    REFRESH_TOKEN("rt+jwt");

    private final String headerType;

    TokenType(String headerType) {
        this.headerType = headerType;
    }

    public String headerType() {
        return headerType;
    }
}
