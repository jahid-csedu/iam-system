package com.example.iamsystem.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    @DisplayName("Without configured origins every other origin is refused")
    void noOrigins_refusesEveryOrigin() {
        CorsConfiguration configuration = configurationFor(List.of());

        assertNull(configuration.checkOrigin("http://localhost:5173"));
    }

    @Test
    @DisplayName("Only the configured origins are allowed, without credentials, on every path")
    void configuredOrigins_areTheOnlyOnesAllowed() {
        CorsConfiguration configuration = configurationFor(List.of("http://localhost:5173"));

        assertEquals("http://localhost:5173", configuration.checkOrigin("http://localhost:5173"));
        assertNull(configuration.checkOrigin("http://localhost:5174"));
        assertFalse(configuration.getAllowCredentials());
        assertEquals(CorsConfig.ALLOWED_METHODS, configuration.getAllowedMethods());
        assertEquals(CorsConfig.ALLOWED_HEADERS, configuration.getAllowedHeaders());
        assertEquals(CorsConfig.PREFLIGHT_CACHE.toSeconds(), configuration.getMaxAge());
    }

    private CorsConfiguration configurationFor(List<String> origins) {
        CorsConfiguration configuration = corsConfig.corsConfigurationSource(new CorsProperties(origins))
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/users"));
        assertNotNull(configuration);
        return configuration;
    }
}
