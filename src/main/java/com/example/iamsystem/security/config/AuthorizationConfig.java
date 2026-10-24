package com.example.iamsystem.security.config;

import com.example.iamsystem.security.JwtAuthenticationEntryPoint;
import com.example.iamsystem.security.filter.JwtRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Which endpoints need a logged-in user. Fine-grained permissions are enforced by {@code @RequirePermission}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AuthorizationConfig {

    /**
     * Under {@code /api/auth/**} but requires a logged-in user; must be matched before the public rules.
     */
    private static final String[] AUTHENTICATED_AUTH_APIS = {
            "/api/auth/authorize"
    };

    private static final String[] PUBLIC_APIS = {
            "/api/tenants/signup",
            "/api/auth/**",
            "/api/password/reset-request",
            "/api/password/reset"
    };

    private static final String[] OPENAPI_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs.yaml",
            "/v3/api-docs/swagger-config",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtRequestFilter jwtRequestFilter;

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(AUTHENTICATED_AUTH_APIS).authenticated()
                        .requestMatchers(OPENAPI_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_APIS).permitAll()
                        .anyRequest().authenticated())
                .authenticationManager(authenticationManager)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
