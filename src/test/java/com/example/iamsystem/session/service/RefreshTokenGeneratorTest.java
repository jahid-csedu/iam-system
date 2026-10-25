package com.example.iamsystem.session.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    @Test
    @DisplayName("Refresh tokens are 256 random bits in URL-safe base64, different every time")
    void generate_isRandomAndUrlSafe() {
        String first = generator.generate();

        assertEquals(43, first.length());
        assertTrue(first.matches("[A-Za-z0-9_-]+"), first);
        assertNotEquals(first, generator.generate());
    }

    @Test
    @DisplayName("The stored hash is the SHA-256 of the token in hex, the same for the same token")
    void hash_isStableSha256Hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", generator.hash("abc"));
        assertEquals(generator.hash("token"), generator.hash("token"));
    }
}
