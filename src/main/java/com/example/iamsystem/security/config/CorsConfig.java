package com.example.iamsystem.security.config;

import com.example.iamsystem.security.userdetails.ActingTenantResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Cross-origin access for browser apps on the origins of {@link CorsProperties}, on every path. Tokens travel in the
 * {@code Authorization} header and in request bodies, never in cookies, so credentials stay off. Requests from any other
 * origin are refused (403); requests without an {@code Origin} header (servers, Postman) are not affected.
 */
@Configuration
public class CorsConfig {

    static final List<String> ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name());
    static final List<String> ALLOWED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, ActingTenantResolver.TENANT_HEADER);
    static final Duration PREFLIGHT_CACHE = Duration.ofHours(1);

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(PREFLIGHT_CACHE);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
