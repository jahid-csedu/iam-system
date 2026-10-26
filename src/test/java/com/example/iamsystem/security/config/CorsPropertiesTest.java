package com.example.iamsystem.security.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("Exact origins with or without a port are valid, and so is an empty list")
    void exactOrigins_areValid() {
        assertTrue(validator.validate(new CorsProperties(
                List.of("https://console.example.com", "http://localhost:5173", "http://127.0.0.1:3000"))).isEmpty());
        assertTrue(validator.validate(new CorsProperties(List.of())).isEmpty());
    }

    @Test
    @DisplayName("Wildcards, paths, trailing slashes and other schemes are not origins")
    void anythingElse_isInvalid() {
        List<String> invalid = List.of("*", "https://*.example.com", "https://console.example.com/", "https://example.com/app",
                "console.example.com", "ftp://example.com", "https://example.com?x=1", "");

        for (String origin : invalid) {
            assertEquals(1, validator.validate(new CorsProperties(List.of(origin))).size(), origin);
        }
    }
}
