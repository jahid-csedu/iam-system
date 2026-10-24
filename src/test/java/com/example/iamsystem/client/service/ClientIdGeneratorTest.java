package com.example.iamsystem.client.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIdGeneratorTest {

    private final ClientIdGenerator generator = new ClientIdGenerator();

    @Test
    @DisplayName("Client ids are 24 URL-safe characters")
    void newClientId_isTwentyFourUrlSafeCharacters() {
        String clientId = generator.newClientId();

        assertEquals(24, clientId.length());
        assertTrue(clientId.matches("[A-Za-z0-9_-]{24}"), clientId);
    }

    @Test
    @DisplayName("Client ids don't repeat")
    void newClientId_isRandom() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(generator.newClientId());
        }

        assertEquals(1000, ids.size());
    }
}
