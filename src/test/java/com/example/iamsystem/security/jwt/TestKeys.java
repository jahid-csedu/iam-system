package com.example.iamsystem.security.jwt;

import com.example.iamsystem.security.config.JwtProperties.SigningKeyProperties;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Fresh RSA keys for tests, in the PEM form {@code iam.jwt.keys} takes.
 */
final class TestKeys {

    private TestKeys() {
    }

    static KeyPair rsaKeyPair(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String pem(KeyPair keyPair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    static SigningKeyProperties configuredKey(String kid) {
        return new SigningKeyProperties(kid, pem(rsaKeyPair(2048)), null);
    }
}
