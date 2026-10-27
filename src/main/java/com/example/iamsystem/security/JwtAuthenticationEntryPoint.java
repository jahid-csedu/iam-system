package com.example.iamsystem.security;

import com.example.iamsystem.common.exception.AuthenticationRequiredException;
import com.example.iamsystem.common.exception.GlobalHandler;
import com.example.iamsystem.security.filter.JwtRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static com.example.iamsystem.common.exception.ErrorMessage.AUTHENTICATION_REQUIRED;

/**
 * Answers 401 for a protected endpoint without a usable access token, through {@link GlobalHandler} like every other
 * error. The detail says why when {@link JwtRequestFilter} found a live session whose account is blocked (locked,
 * deactivated, tenant or client suspended); otherwise it only says that a valid token is needed.
 */
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        log.warn("Unauthorized access attempt to URI: {} - {}", request.getRequestURI(), authException.getMessage());
        String reason = request.getAttribute(JwtRequestFilter.REFUSAL_REASON) instanceof String refusal ? refusal : AUTHENTICATION_REQUIRED;
        exceptionResolver.resolveException(request, response, null, new AuthenticationRequiredException(reason));
    }
}
