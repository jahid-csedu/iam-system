package com.example.iamsystem.security.jwt;

import com.example.iamsystem.constant.TokenType;
import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.example.iamsystem.constant.JwtConstant.ACCESS_TOKEN_SECRET_KEY;
import static com.example.iamsystem.constant.JwtConstant.JWT_ACCESS_TOKEN_VALIDITY;
import static com.example.iamsystem.constant.JwtConstant.JWT_REFRESH_TOKEN_VALIDITY;
import static com.example.iamsystem.constant.JwtConstant.REFRESH_TOKEN_SECRET;
import static com.example.iamsystem.constant.TokenType.ACCESS_TOKEN;

@Component
@Slf4j
public class JwtTokenUtil implements Serializable {

    public static final String VERSION = "version";
    public static final String AUTHORITIES = "authorities";

    public String getUsernameFromToken(String token, TokenType tokenType) {
        log.debug("Extracting username from token of type: {}", tokenType);
        return getClaimFromToken(token, Claims::getSubject, tokenType);
    }

    public Date getExpirationDateFromToken(String token, TokenType tokenType) {
        log.debug("Extracting expiration date from token of type: {}", tokenType);
        return getClaimFromToken(token, Claims::getExpiration, tokenType);
    }

    public int getVersionFromToken(String token, TokenType tokenType) {
        int version = (int) getClaimFromToken(token, claims -> claims.getOrDefault(VERSION, 0), tokenType);
        log.debug("Extracted version from token: {}", version);
        return version;
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver, TokenType tokenType) {
        final Claims claims = getAllClaimsFromToken(token, tokenType);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token, TokenType tokenType) {
        log.debug("Getting all claims from token of type: {}", tokenType);
        return Jwts.parser()
                .verifyWith(getSecretKey(tokenType))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token, TokenType tokenType) {
        final Date expiration = getExpirationDateFromToken(token, tokenType);
        boolean expired = expiration.before(new Date());
        if (expired) {
            log.debug("Token of type {} is expired.", tokenType);
        } else {
            log.debug("Token of type {} is not expired.", tokenType);
        }
        return expired;
    }

    public String generateToken(UserDetails userDetails, TokenType tokenType) {
        log.debug("Generating {} token for user: {}", tokenType, userDetails.getUsername());
        Map<String, Object> claims = new HashMap<>();
        if (tokenType.equals(ACCESS_TOKEN)) {
            claims.put(AUTHORITIES, userDetails.getAuthorities());
        }
        User user = getUser(userDetails);
        claims.put(VERSION, user.getVersion());
        String token = doGenerateToken(claims, userDetails.getUsername(), tokenType);
        log.info("Successfully generated {} token for user: {}", tokenType, userDetails.getUsername());
        return token;
    }

    private String doGenerateToken(Map<String, Object> claims, String subject, TokenType tokenType) {
        long expiryTime = tokenType.equals(ACCESS_TOKEN) ? JWT_ACCESS_TOKEN_VALIDITY : JWT_REFRESH_TOKEN_VALIDITY;

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiryTime * 1000))
                .signWith(getSecretKey(tokenType))
                .compact();
    }

    public boolean validateToken(String token, UserDetails userDetails, TokenType tokenType) {
        log.debug("Validating {} token for user: {}", tokenType, userDetails.getUsername());
        final String username = getUsernameFromToken(token, tokenType);
        User user = getUser(userDetails);
        int version = getVersionFromToken(token, tokenType);
        if(user.getVersion() != version) {
            log.error("Token version mismatch. Extracted version: {}, user version: {}", version, user.getVersion());
            return false;
        }
        boolean isValid = (username.equals(userDetails.getUsername()) && !isTokenExpired(token, tokenType));
        if (isValid) {
            log.info("{} token is valid for user: {}", tokenType, userDetails.getUsername());
        } else {
            log.warn("{} token is invalid for user: {}", tokenType, userDetails.getUsername());
        }
        return isValid;
    }

    private static User getUser(UserDetails userDetails) {
        DefaultUserDetails defaultUserDetails = (DefaultUserDetails) userDetails;
        return defaultUserDetails.user();
    }

    public boolean validateToken(String token, TokenType tokenType) {
        log.debug("Validating {} token without user details.", tokenType);
        try {
            getAllClaimsFromToken(token, tokenType);
            log.info("{} token is valid.", tokenType);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("{} token validation failed: {}", tokenType, e.getMessage());
            return false;
        }
    }

    private SecretKey getSecretKey(TokenType tokenType) {
        if (tokenType.equals(ACCESS_TOKEN)) {
            return Keys.hmacShaKeyFor(ACCESS_TOKEN_SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        }
        return Keys.hmacShaKeyFor(REFRESH_TOKEN_SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
