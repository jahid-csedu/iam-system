package com.example.iamsystem.security.jwt;

import com.example.iamsystem.common.util.SecretMask;

/**
 * A signed access token and its lifetime in seconds, the same number its {@code exp} claim was computed from.
 */
public record IssuedAccessToken(String token, long expiresIn) {

    @Override
    public String toString() {
        return "IssuedAccessToken[token=" + SecretMask.MASK + ", expiresIn=" + expiresIn + "]";
    }
}
