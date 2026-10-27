package com.example.iamsystem.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    private final OpenAPI openApi = new OpenApiConfig().openAPI();

    @Test
    @DisplayName("Swagger asks for a bearer token without its prefix and sends it on every endpoint by default")
    void bearerScheme_explainsThePrefix() {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);

        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals(OpenApiConfig.BEARER_HINT, scheme.getDescription());
        assertEquals(List.of(OpenApiConfig.BEARER_SCHEME),
                List.copyOf(openApi.getSecurity().getFirst().keySet()));
    }
}
