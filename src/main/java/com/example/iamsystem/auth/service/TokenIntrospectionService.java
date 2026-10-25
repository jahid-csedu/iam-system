package com.example.iamsystem.auth.service;

import com.example.iamsystem.auth.dto.IntrospectionRequest;
import com.example.iamsystem.auth.dto.IntrospectionResponse;
import com.example.iamsystem.security.jwt.TokenVerifier;
import com.example.iamsystem.session.repository.UserSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tells other applications whether an access token is still good right now, which local JWKS verification can't: an
 * access token is active only while it is validly signed and unexpired, its session is live (no logout or password
 * change since), its user is active and unlocked, and its tenant is active.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenIntrospectionService {

    private static final String TOKEN_TYPE = "token_type";
    private static final String BEARER = "Bearer";

    private final TokenVerifier tokenVerifier;
    private final UserSessionRepository sessionRepository;

    public IntrospectionResponse introspect(IntrospectionRequest request) {
        Claims claims;
        Long userId;
        Long sessionId;
        try {
            claims = tokenVerifier.parseAccessToken(request.token());
            userId = tokenVerifier.userIdOf(claims);
            sessionId = tokenVerifier.sessionIdOf(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Introspected an invalid or expired access token: {}", e.getMessage());
            return IntrospectionResponse.inactive();
        }
        if (sessionId == null || sessionRepository.findActor(sessionId, userId, Instant.now()).isEmpty()) {
            return IntrospectionResponse.inactive();
        }
        return IntrospectionResponse.active(responseClaims(claims));
    }

    /**
     * The token's claims, with {@code iat} and {@code exp} in epoch seconds as in the token itself.
     */
    private static Map<String, Object> responseClaims(Claims claims) {
        Map<String, Object> response = new HashMap<>(claims);
        response.put(Claims.ISSUED_AT, claims.getIssuedAt().toInstant().getEpochSecond());
        response.put(Claims.EXPIRATION, claims.getExpiration().toInstant().getEpochSecond());
        response.put(TOKEN_TYPE, BEARER);
        return response;
    }
}
