package com.example.iamsystem.security.jwt;

import com.example.iamsystem.constant.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.example.iamsystem.constant.JwtConstant.ACCESS_TOKEN_SECRET_KEY;
import static com.example.iamsystem.constant.JwtConstant.JWT_ACCESS_TOKEN_VALIDITY;
import static com.example.iamsystem.constant.JwtConstant.JWT_REFRESH_TOKEN_VALIDITY;
import static com.example.iamsystem.constant.JwtConstant.REFRESH_TOKEN_SECRET;
import static com.example.iamsystem.constant.TokenType.ACCESS_TOKEN;

@Component
public class JwtTokenUtil implements Serializable {

    // Retrieve username from jwt token
    public String getUsernameFromToken(String token, TokenType tokenType) {
        return getClaimFromToken(token, Claims::getSubject, tokenType);
    }

    // Retrieve expiration date from jwt token
    public Date getExpirationDateFromToken(String token, TokenType tokenType) {
        return getClaimFromToken(token, Claims::getExpiration, tokenType);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver, TokenType tokenType) {
        final Claims claims = getAllClaimsFromToken(token, tokenType);
        return claimsResolver.apply(claims);
    }

    // For retrieving any information from token we will need the secret key
    private Claims getAllClaimsFromToken(String token, TokenType tokenType) {
        return Jwts.parser()
                .verifyWith(getSecretKey(tokenType))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Check if the token has expired
    private Boolean isTokenExpired(String token, TokenType tokenType) {
        final Date expiration = getExpirationDateFromToken(token, tokenType);
        return expiration.before(new Date());
    }

    // Generate token for user
    public String generateToken(UserDetails userDetails, TokenType tokenType) {
        Map<String, Object> claims = new HashMap<>();
        if (tokenType.equals(ACCESS_TOKEN)) {
            claims.put("authorities", userDetails.getAuthorities());
        }
        return doGenerateToken(claims, userDetails.getUsername(), tokenType);
    }

    // While creating the token -
    // 1. Define  claims of the token, like Issuer, Expiration, Subject, and the ID
    // 2. Sign the JWT using the HS512 algorithm and secret key.
    // 3. According to JWS Compact Serialization(https://tools.ietf.org/html/draft-ietf-jose-json-web-signature-41#section-3.1)
    // Compaction of the JWT to a URL-safe string
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

    // Validate token
    public boolean validateToken(String token, UserDetails userDetails, TokenType tokenType) {
        final String username = getUsernameFromToken(token, tokenType);

        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token, tokenType));
    }

    public boolean validateToken(String token, TokenType tokenType) {
        try {
            getAllClaimsFromToken(token, tokenType);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
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
