package com.example.iamsystem.security.filter;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.jwt.TokenVerifier;
import com.example.iamsystem.security.userdetails.ActingTenantResolver;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.session.repository.UserSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.example.iamsystem.security.userdetails.ActingTenantResolver.TENANT_HEADER;

/**
 * Authenticates requests that carry a Bearer access token by putting an {@link AuthenticatedActor} into the security
 * context. A token that is malformed or expired, whose session has ended (logout, password change), or that belongs to
 * an inactive or locked user, leaves the request unauthenticated, so protected endpoints answer 401. When the session is
 * live but its account is blocked (locked, deactivated, tenant or client suspended), the reason is kept in the request
 * attribute {@link #REFUSAL_REASON} for that 401 ({@code JwtAuthenticationEntryPoint}); only the token's holder sees it.
 * {@link ActingTenantResolver} then decides which tenant the request works in; its errors (a malformed or unknown
 * {@code X-Tenant-Id}) are answered through the regular exception handling.
 */
@Component
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    public static final String REFUSAL_REASON = JwtRequestFilter.class.getName() + ".refusalReason";
    private static final String BEARER = "Bearer ";

    private final TokenVerifier tokenVerifier;
    private final UserSessionRepository sessionRepository;
    private final ActingTenantResolver actingTenantResolver;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtRequestFilter(TokenVerifier tokenVerifier, UserSessionRepository sessionRepository,
                            ActingTenantResolver actingTenantResolver,
                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.tokenVerifier = tokenVerifier;
        this.sessionRepository = sessionRepository;
        this.actingTenantResolver = actingTenantResolver;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<AuthenticatedActor> actor = extractToken(request).flatMap(token -> findActor(token, request));
            if (actor.isPresent()) {
                try {
                    authenticate(actingTenantResolver.resolve(actor.get(), request.getHeader(TENANT_HEADER)), request);
                } catch (DataNotFoundException | UserInputNotValidException e) {
                    exceptionResolver.resolveException(request, response, null, e);
                    return;
                }
            }
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

    private Optional<AuthenticatedActor> findActor(String token, HttpServletRequest request) {
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

        if (sessionId == null) {
            log.warn("Rejected the access token of user {}: no session", userId);
            return Optional.empty();
        }
        Instant now = Instant.now();
        Optional<AuthenticatedActor> actor = sessionRepository.findActor(sessionId, userId, now);
        if (actor.isEmpty()) {
            Optional<String> reason = sessionRepository.findAccountState(sessionId, userId, now)
                    .flatMap(state -> state.refusalReason(now));
            reason.ifPresent(message -> request.setAttribute(REFUSAL_REASON, message));
            log.warn("Rejected the access token of user {}: {}", userId, reason.orElse("session ended"));
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
