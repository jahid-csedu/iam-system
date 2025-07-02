package com.example.iamsystem.security.config;

import com.example.iamsystem.security.JwtAuthenticationEntryPoint;
import com.example.iamsystem.security.filter.JwtRequestFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class AuthorizationConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtRequestFilter jwtRequestFilter;

    private static final String[] PERMITTED_GET_APIS = {
            "/api/roles/**",
            "/api/permissions/**"
    };

    private static final String[] PERMITTED_PUBLIC_APIS = {
            "/api/users/register/**",
            "/api/auth/**",
            "/api/password/reset-request",
            "/api/password/reset",
    };

    private static final String[] WHITELISTED_OPENAPI_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs.yaml",
            "/v3/api-docs/swagger-config",
            "/swagger-resources/**",
            "/webjars/**"
    };


    private static final String[] SPECIAL_PRIVATE_APIS = {
            "/api/auth/authorize"
    };

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        log.info("Configuring Security Filter Chain...");
        http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> {
                            request.requestMatchers(SPECIAL_PRIVATE_APIS).authenticated();
                            log.debug("Permitting access to special private APIs: {}", String.join(", ", SPECIAL_PRIVATE_APIS));
                            request.requestMatchers(WHITELISTED_OPENAPI_ENDPOINTS).permitAll();
                            log.debug("Permitting access to OpenAPI endpoints: {}", String.join(", ", WHITELISTED_OPENAPI_ENDPOINTS));
                            request.requestMatchers(PERMITTED_PUBLIC_APIS).permitAll();
                            log.debug("Permitting access to public APIs: {}", String.join(", ", PERMITTED_PUBLIC_APIS));
                            request.requestMatchers(HttpMethod.GET, PERMITTED_GET_APIS).permitAll();
                            log.debug("Permitting GET access to APIs: {}", String.join(", ", PERMITTED_GET_APIS));
                            request.anyRequest().authenticated();
                            log.debug("All other requests require authentication.");
                        }
                )
                .authenticationManager(authenticationManager)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("Security Filter Chain configured successfully.");
        return http.build();

    }
}
