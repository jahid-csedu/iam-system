package com.example.management.security.jwt;

import com.example.management.constant.JwtConstant;
import com.example.management.constant.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenUtil implements Serializable {

    //retrieve username from jwt token
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public String getUsernameFromRefreshToken(String token) {
        return getClaimFromRefreshToken(token, Claims::getSubject);
    }

    //retrieve expiration date from jwt token
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }


    public Date getExpirationDateFromRefreshToken(String token) {
        return getClaimFromRefreshToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    public <T> T getClaimFromRefreshToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromRefreshToken(token);
        return claimsResolver.apply(claims);
    }

    //for retrieveing any information from token we will need the secret key
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().setSigningKey(JwtConstant.SECRET_KEY).parseClaimsJws(token).getBody();
    }

    private Claims getAllClaimsFromRefreshToken(String token) {
        return Jwts.parser().setSigningKey(JwtConstant.REFRESH_TOKEN_SECRET).parseClaimsJws(token).getBody();
    }

    //check if the token has expired
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    private Boolean isRefreshTokenExpired(String token) {
        final Date expiration = getExpirationDateFromRefreshToken(token);
        return expiration.before(new Date());
    }

    //generate token for user
    public String generateToken(UserDetails userDetails, TokenType tokenType) {
        Map<String, Object> claims = new HashMap<>();
        if(tokenType.equals(TokenType.ACCESS_TOKEN)){
            claims.put("authorities", userDetails.getAuthorities());
            return doGenerateToken(claims, userDetails.getUsername(), JwtConstant.JWT_ACCESS_TOKEN_VALIDITY, JwtConstant.SECRET_KEY);
        }
        else if(tokenType.equals(TokenType.REFRESH_TOKEN)) {
            return doGenerateToken(claims, userDetails.getUsername(), JwtConstant.JWT_REFRESH_TOKEN_VALIDITY, JwtConstant.REFRESH_TOKEN_SECRET);
        }
        return null;
    }

    //while creating the token -
    //1. Define  claims of the token, like Issuer, Expiration, Subject, and the ID
    //2. Sign the JWT using the HS512 algorithm and secret key.
    //3. According to JWS Compact Serialization(https://tools.ietf.org/html/draft-ietf-jose-json-web-signature-41#section-3.1)
    //   compaction of the JWT to a URL-safe string
    private String doGenerateToken(Map<String, Object> claims, String subject, long expiryTime, String secret) {

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiryTime * 1000))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    //validate token
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);

        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public Boolean validateRefreshToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromRefreshToken(token);

        return (username.equals(userDetails.getUsername()) && !isRefreshTokenExpired(token));
    }
}
