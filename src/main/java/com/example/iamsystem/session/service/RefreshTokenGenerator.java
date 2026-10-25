package com.example.iamsystem.session.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens are 256 random bits (URL-safe base64); the database keeps only their SHA-256 hash, so a leaked table
 * holds no usable tokens. A plain hash is enough: the tokens are random, not guessable like passwords.
 */
@Component
public class RefreshTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final String SHA_256 = "SHA-256";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
