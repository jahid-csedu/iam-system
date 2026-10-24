package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

import static com.example.iamsystem.security.jwt.JwtConstant.ACCESS_TOKEN_SECRET_KEY;
import static com.example.iamsystem.security.jwt.JwtConstant.REFRESH_TOKEN_SECRET;
import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;

/**
 * Issues and reads the signed tokens. Access and refresh tokens use different keys; their lifetimes and the access
 * token's user claims come from the client's token settings. The subject is the user id (usernames
 * are only unique per tenant); {@code client_id} names the client the user logged in through; {@code version} is the
 * user's version, so any change to the user (roles, password) invalidates the tokens issued before it.
 */
@Component
public class JwtTokenUtil {

    static final String VERSION = "version";
    static final String CLIENT_ID = "client_id";

    /**
     * Lives as long as the client's access-token lifetime and carries the user claims the client selected.
     */
    public String generateAccessToken(User user, Client client) {
        JwtBuilder token = baseToken(user, client, client.getAccessTokenTtlSeconds(), ACCESS_TOKEN);
        client.getTokenClaims().forEach(claim -> {
            Object value = claimValue(claim, user);
            if (value != null) {
                token.claim(claim.claimName(), value);
            }
        });
        return token.compact();
    }

    /**
     * Lives as long as the client's refresh-token lifetime; carries no user claims.
     */
    public String generateRefreshToken(User user, Client client) {
        return baseToken(user, client, client.getRefreshTokenTtlSeconds(), REFRESH_TOKEN).compact();
    }

    private JwtBuilder baseToken(User user, Client client, int lifetimeSeconds, TokenType tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(VERSION, user.getVersion())
                .claim(CLIENT_ID, client.getClientId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(lifetimeSeconds)))
                .signWith(secretKey(tokenType));
    }

    private static Object claimValue(TokenClaim claim, User user) {
        return switch (claim) {
            case USERNAME -> user.getUsername();
            case EMAIL -> user.getEmail();
            case FULL_NAME -> user.getFullName();
            case USER_TYPE -> user.getUserType().name();
            case TENANT_ID -> user.getTenant().getId();
        };
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
