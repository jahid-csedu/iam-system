package com.example.iamsystem.security.filter;

import com.example.iamsystem.constant.ErrorMessage;
import com.example.iamsystem.constant.JwtConstant;
import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.security.user.DefaultUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

import static com.example.iamsystem.constant.TokenType.ACCESS_TOKEN;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {
    private final DefaultUserDetailsService userDetailsService;
    private final JwtTokenUtil tokenUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        log.debug("Processing request for URI: {}", request.getRequestURI());
        String jwtToken = extractToken(request);
        if (Objects.nonNull(jwtToken)) {
            try {
                String username = tokenUtil.getUsernameFromToken(jwtToken, ACCESS_TOKEN);
                if (Objects.nonNull(username) && Objects.isNull(SecurityContextHolder.getContext().getAuthentication())) {
                    log.debug("Authenticating user: {}", username);
                    authenticateUser(request, jwtToken, username);
                    log.info("User '{}' authenticated successfully.", username);
                }
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
                log.error("JWT validation failed for URI {}: {}", request.getRequestURI(), e.getMessage());
                throw new com.example.iamsystem.exception.JwtException(ErrorMessage.INVALID_TOKEN);
            }
        } else {
            log.debug("No JWT token found in request for URI: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        final String authorizationHeader = request.getHeader(JwtConstant.REQUEST_HEADER);
        if (Objects.isNull(authorizationHeader) || !authorizationHeader.startsWith(JwtConstant.BEARER)) {
            log.error("Authorization header is missing or token doesn't start with Bearer for URI: {}", request.getRequestURI());
            return null;
        }
        log.debug("JWT token extracted from Authorization header for URI: {}", request.getRequestURI());
        return authorizationHeader.replace(JwtConstant.BEARER, "");
    }

    private void authenticateUser(HttpServletRequest request, String jwtToken, String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (tokenUtil.validateToken(jwtToken, userDetails, ACCESS_TOKEN)) {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            log.debug("SecurityContextHolder updated with authentication for user: {}", username);
        } else {
            log.warn("JWT token validation failed for user: {}", username);
        }
    }
}
