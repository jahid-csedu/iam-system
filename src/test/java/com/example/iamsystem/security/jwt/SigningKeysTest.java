package com.example.iamsystem.security.jwt;

import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.security.config.JwtProperties.SigningKeyProperties;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;

import static com.example.iamsystem.security.jwt.TestKeys.configuredKey;
import static com.example.iamsystem.security.jwt.TestKeys.pem;
import static com.example.iamsystem.security.jwt.TestKeys.rsaKeyPair;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigningKeysTest {

    @Test
    @DisplayName("A configured PEM private key signs, and its public key is derived from it")
    void configuredKey_derivesThePublicKey() {
        KeyPair pair = rsaKeyPair(2048);

        SigningKeys keys = signingKeys(new SigningKeyProperties("key-1", pem(pair), null));

        assertEquals("key-1", keys.current().kid());
        assertEquals(pair.getPrivate(), keys.current().privateKey());
        assertEquals(pair.getPublic(), keys.current().publicKey());
    }

    @Test
    @DisplayName("A key given as plain base64 without PEM header lines is accepted")
    void keyWithoutPemLines_isAccepted() {
        KeyPair pair = rsaKeyPair(2048);
        String base64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

        SigningKeys keys = signingKeys(new SigningKeyProperties("key-1", base64, null));

        assertEquals(pair.getPublic(), keys.current().publicKey());
    }

    @Test
    @DisplayName("The first configured key signs and every configured key can verify")
    void severalKeys_firstSignsAllVerify() {
        SigningKeys keys = signingKeys(configuredKey("new"), configuredKey("old"));

        assertEquals("new", keys.current().kid());
        assertEquals(List.of("new", "old"), keys.all().stream().map(SigningKey::kid).toList());
        assertTrue(keys.findPublicKey("old").isPresent());
        assertFalse(keys.findPublicKey("unknown").isPresent());
    }

    @Test
    @DisplayName("A key is read from its PEM file when it names a location instead")
    void keyFromALocation_isReadFromTheFile(@TempDir Path directory) throws IOException {
        KeyPair pair = rsaKeyPair(2048);
        Path file = Files.writeString(directory.resolve("jwt-key.pem"), pem(pair));

        SigningKeys keys = signingKeys(new SigningKeyProperties("key-1", null, file.toUri().toString()));

        assertEquals(pair.getPublic(), keys.current().publicKey());
    }

    @Test
    @DisplayName("A location that can't be read stops the startup and names the key and the location")
    void unreadableLocation_failsTheStartup(@TempDir Path directory) {
        String location = directory.resolve("missing.pem").toUri().toString();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signingKeys(new SigningKeyProperties("key-1", null, location)));

        assertEquals("Can't read JWT signing key 'key-1' from " + location, error.getMessage());
    }

    @Test
    @DisplayName("A key needs either its PEM or a location, not both and not neither")
    void keySource_isExactlyOne() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(new SigningKeyProperties("k", "pem", null)).isEmpty());
            assertTrue(factory.getValidator().validate(new SigningKeyProperties("k", null, "file:/k.pem")).isEmpty());
            assertEquals(1, factory.getValidator().validate(new SigningKeyProperties("k", "pem", "file:/k.pem")).size());
            assertEquals(1, factory.getValidator().validate(new SigningKeyProperties("k", " ", null)).size());
        }
    }

    @Test
    @DisplayName("A key whose settings are all empty counts as no key")
    void emptyKeyEntry_isNoKey() {
        JwtProperties properties = new JwtProperties("https://iam.test", List.of(new SigningKeyProperties("", "", "")), true);

        assertTrue(properties.keys().isEmpty());
    }

    @Test
    @DisplayName("Without configured keys a temporary 2048-bit key is generated where that's allowed")
    void noKeys_generatesATemporaryKey() {
        SigningKeys keys = new SigningKeys(new JwtProperties("https://iam.test", List.of(), true), new DefaultResourceLoader());

        assertTrue(keys.current().kid().startsWith("temporary-"));
        assertEquals(2048, keys.current().publicKey().getModulus().bitLength());
    }

    @Test
    @DisplayName("Without configured keys outside the local profile the startup stops and says what to set")
    void noKeys_whereNotAllowed_failTheStartup() {
        JwtProperties properties = new JwtProperties("https://iam.test", List.of(), false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new SigningKeys(properties, new DefaultResourceLoader()));

        assertTrue(error.getMessage().startsWith("No JWT signing key configured: set IAM_JWT_KEYS_0_KID"), error.getMessage());
    }

    @Test
    @DisplayName("A key that isn't a PKCS#8 RSA private key fails startup without revealing the key")
    void invalidKey_failsWithoutRevealingIt() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signingKeys(new SigningKeyProperties("broken", "c2VjcmV0LW5vdC1hLWtleQ==", null)));

        assertEquals("JWT signing key 'broken' is not a PKCS#8 RSA private key in PEM", error.getMessage());
    }

    @Test
    @DisplayName("A key shorter than 2048 bits is refused")
    void shortKey_isRefused() {
        SigningKeyProperties shortKey = new SigningKeyProperties("short", pem(rsaKeyPair(1024)), null);

        assertThrows(IllegalStateException.class, () -> signingKeys(shortKey));
    }

    @Test
    @DisplayName("Two keys with the same id are refused")
    void duplicateKeyIds_areRefused() {
        assertThrows(IllegalStateException.class, () -> signingKeys(configuredKey("same"), configuredKey("same")));
    }

    private static SigningKeys signingKeys(SigningKeyProperties... keys) {
        return new SigningKeys(new JwtProperties("https://iam.test", List.of(keys), false), new DefaultResourceLoader());
    }
}
