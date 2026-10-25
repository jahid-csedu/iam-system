package com.example.iamsystem.security.filter;

import com.example.iamsystem.security.jwt.TokenVerifier;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.session.repository.UserSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests that carry a Bearer access token by putting an {@link AuthenticatedActor} into the security
 * context. A token that is malformed or expired, whose session has ended (logout, password change), or that belongs to
 * an inactive or locked user, leaves the request unauthenticated, so protected endpoints answer 401.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final TokenVerifier tokenVerifier;
    private final UserSessionRepository sessionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractToken(request)
                    .flatMap(this::findActor)
                    .ifPresent(actor -> authenticate(actor, request));
        }
        filterChain.doFilter(request, response);
    }

    private static Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER.length()));
    }

    private Optional<AuthenticatedActor> findActor(String token) {
        Long userId;
        Long sessionId;
        try {
            Claims claims = tokenVerifier.parseAccessToken(token);
            userId = tokenVerifier.userIdOf(claims);
            sessionId = tokenVerifier.sessionIdOf(claims);
        } catch (ExpiredJwtException e) {
            log.debug("Ignoring an expired access token of user {}", e.getClaims().getSubject());
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Ignoring an invalid access token: {}", e.getMessage());
            return Optional.empty();
        }

        Optional<AuthenticatedActor> actor = sessionId == null
                ? Optional.empty()
                : sessionRepository.findActor(sessionId, userId, Instant.now());
        if (actor.isEmpty()) {
            log.warn("Rejected the access token of user {}: session ended, or the account is inactive or locked", userId);
        }
        return actor;
    }

    private static void authenticate(AuthenticatedActor actor, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
