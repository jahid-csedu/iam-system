package com.example.iamsystem.security;

import com.example.iamsystem.common.exception.ExceptionResponse;
import com.example.iamsystem.security.filter.JwtRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static com.example.iamsystem.common.exception.ErrorMessage.AUTHENTICATION_REQUIRED;

/**
 * Answers 401 for a protected endpoint without a usable access token, with the usual error body. The message says why
 * when {@link JwtRequestFilter} found a live session whose account is blocked (locked, deactivated, tenant or client
 * suspended); otherwise it only says that a valid token is needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        log.warn("Unauthorized access attempt to URI: {} - {}", request.getRequestURI(), authException.getMessage());
        String message = request.getAttribute(JwtRequestFilter.REFUSAL_REASON) instanceof String reason ? reason : AUTHENTICATION_REQUIRED;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), new ExceptionResponse(HttpServletResponse.SC_UNAUTHORIZED, message));
    }
}
