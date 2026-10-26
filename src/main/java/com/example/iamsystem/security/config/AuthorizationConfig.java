package com.example.iamsystem.security.config;

import com.example.iamsystem.auth.controller.JwksController;
import com.example.iamsystem.security.JwtAuthenticationEntryPoint;
import com.example.iamsystem.security.filter.JwtRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Which endpoints need a logged-in user. Fine-grained permissions are enforced by {@code @RequirePermission}; which
 * browser origins may call the API is {@link CorsConfig}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AuthorizationConfig {

    private static final String[] PUBLIC_APIS = {
            "/api/tenants/signup",
            "/api/auth/authenticate",
            "/api/auth/token/refresh",
            "/api/auth/introspect",
            "/api/password/reset-request",
            "/api/password/reset",
            JwksController.JWKS_PATH
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
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
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
