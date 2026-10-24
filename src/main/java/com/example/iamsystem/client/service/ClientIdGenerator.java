package com.example.iamsystem.client.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates public client ids: 24 URL-safe characters from 18 random bytes.
 */
@Component
public class ClientIdGenerator {

    private static final int RANDOM_BYTES = 18;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String newClientId() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
