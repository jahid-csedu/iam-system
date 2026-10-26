package com.example.iamsystem.security.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * The browser origins allowed to call this API from another origin ({@code iam.cors.allowed-origins}, comma-separated),
 * e.g. the management console's {@code https://console.example.com}. Each is an exact origin: scheme, host and optional
 * port, without path, trailing slash or wildcard; anything else stops startup. Without any, no other origin is allowed.
 */
@Validated
@ConfigurationProperties("iam.cors")
public record CorsProperties(@DefaultValue List<@Pattern(regexp = ORIGIN, message = ORIGIN_MESSAGE) String> allowedOrigins) {

    static final String ORIGIN = "https?://[^/\\s?#*]+";
    static final String ORIGIN_MESSAGE = "must be an exact origin such as https://console.example.com (no path or wildcard)";
}
