package com.example.iamsystem.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI description behind Swagger UI: every endpoint takes a Bearer access token unless it says otherwise.
 */
@Configuration
public class OpenApiConfig {

    /**
     * The name the controllers' {@code @SecurityRequirement} refers to.
     */
    public static final String BEARER_SCHEME = "Bearer Authentication";

    /**
     * Shown in Swagger UI's Authorize dialog: Swagger adds the {@code Bearer } prefix itself.
     */
    static final String BEARER_HINT = "Paste only the access_token, without 'Bearer ' (Swagger adds it).";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme()))
                .info(new Info().title("IAM System")
                        .description("A complete system for Identity and Access Management")
                        .version("1.0.0"));
    }

    private static SecurityScheme bearerScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(BEARER_HINT);
    }
}
