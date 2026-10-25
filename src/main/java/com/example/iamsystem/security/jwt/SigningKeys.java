package com.example.iamsystem.security.jwt;

import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.security.config.JwtProperties.SigningKeyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The platform's token signing keys: the configured ones, or a temporary key generated at startup when none is configured.
 * The first key signs; every key verifies, which lets a new key take over while tokens of the old one are still alive.
 */
@Component
@Slf4j
public class SigningKeys {

    private final List<SigningKey> keys;

    public SigningKeys(JwtProperties properties) {
        this.keys = properties.keys().isEmpty() ? List.of(temporaryKey()) : configuredKeys(properties.keys());
    }

    /**
     * The key new tokens are signed with.
     */
    public SigningKey current() {
        return keys.getFirst();
    }

    /**
     * Every key a token may have been signed with, the current one first.
     */
    public List<SigningKey> all() {
        return keys;
    }

    public Optional<RSAPublicKey> findPublicKey(String kid) {
        return keys.stream().filter(key -> key.kid().equals(kid)).findFirst().map(SigningKey::publicKey);
    }

    private static List<SigningKey> configuredKeys(List<SigningKeyProperties> configured) {
        List<SigningKey> parsed = configured.stream()
                .map(key -> RsaKeyParser.parse(key.kid(), key.privateKey()))
                .toList();
        if (parsed.stream().map(SigningKey::kid).distinct().count() < parsed.size()) {
            throw new IllegalStateException("JWT signing key ids must be unique");
        }
        log.info("Loaded {} JWT signing key(s); signing with '{}'", parsed.size(), parsed.getFirst().kid());
        return parsed;
    }

    private static SigningKey temporaryKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RsaKeyParser.RSA);
            generator.initialize(RsaKeyParser.MIN_KEY_BITS);
            KeyPair pair = generator.generateKeyPair();
            SigningKey key = new SigningKey("temporary-" + UUID.randomUUID(),
                    (RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
            log.warn("No JWT signing key configured (iam.jwt.keys): generated temporary key '{}'. "
                    + "Tokens become invalid when the application restarts.", key.kid());
            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation is not available", e);
        }
    }
}
