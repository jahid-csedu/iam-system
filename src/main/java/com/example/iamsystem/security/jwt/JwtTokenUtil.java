package com.example.iamsystem.security.jwt;

import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

import static com.example.iamsystem.security.jwt.JwtConstant.ACCESS_TOKEN_SECRET_KEY;
import static com.example.iamsystem.security.jwt.JwtConstant.JWT_ACCESS_TOKEN_VALIDITY;
import static com.example.iamsystem.security.jwt.JwtConstant.JWT_REFRESH_TOKEN_VALIDITY;
import static com.example.iamsystem.security.jwt.JwtConstant.REFRESH_TOKEN_SECRET;
import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;

/**
 * Issues and reads the signed tokens. Access and refresh tokens use different keys. The subject is the user id (usernames
 * are only unique per tenant); {@code client_id} names the client the user logged in through; {@code version} is the
 * user's version, so any change to the user (roles, password) invalidates the tokens issued before it.
 */
@Component
public class JwtTokenUtil {

    static final String VERSION = "version";
    static final String CLIENT_ID = "client_id";

    public String generateToken(User user, String clientId, TokenType tokenType) {
        long validitySeconds = tokenType == ACCESS_TOKEN ? JWT_ACCESS_TOKEN_VALIDITY : JWT_REFRESH_TOKEN_VALIDITY;
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(VERSION, user.getVersion())
                .claim(CLIENT_ID, clientId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + validitySeconds * 1000))
                .signWith(secretKey(tokenType))
                .compact();
    }

    /**
     * Verifies the signature and expiry and returns the claims.
     *
     * @throws JwtException             if the token is malformed, wrongly signed or expired
     * @throws IllegalArgumentException if the token is blank
     */
    public Claims parseClaims(String token, TokenType tokenType) {
        return Jwts.parser()
                .verifyWith(secretKey(tokenType))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * @throws IllegalArgumentException if the subject isn't a user id
     */
    public Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String clientIdOf(Claims claims) {
        return claims.get(CLIENT_ID, String.class);
    }

    /**
     * The user version the token was issued for, or {@code null} if the token carries none.
     */
    public Integer versionOf(Claims claims) {
        return claims.get(VERSION, Integer.class);
    }

    /**
     * Whether verified claims were issued for this user and its current version (not revoked since).
     */
    public boolean isIssuedFor(Claims claims, User user) {
        return String.valueOf(user.getId()).equals(claims.getSubject())
                && Integer.valueOf(user.getVersion()).equals(versionOf(claims));
    }

    /**
     * Checks signature and expiry only.
     */
    public boolean isValid(String token, TokenType tokenType) {
        try {
            parseClaims(token, tokenType);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private static SecretKey secretKey(TokenType tokenType) {
        String secret = tokenType == ACCESS_TOKEN ? ACCESS_TOKEN_SECRET_KEY : REFRESH_TOKEN_SECRET;
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
