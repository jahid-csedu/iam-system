package com.example.iamsystem.auth.dto;

import com.example.iamsystem.security.jwt.SigningKey;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;

/**
 * The public half of a signing key as an RSA JSON Web Key (RFC 7517/7518): modulus {@code n} and exponent {@code e}.
 */
public record JwkDto(String kty, String use, String alg, String kid, String n, String e) {

    private static final String RSA = "RSA";
    private static final String SIGNATURE = "sig";
    private static final String RS256 = "RS256";

    public static JwkDto of(SigningKey key) {
        return new JwkDto(RSA, SIGNATURE, RS256, key.kid(),
                base64Url(key.publicKey().getModulus()), base64Url(key.publicKey().getPublicExponent()));
    }

    /**
     * Unsigned big-endian bytes, without the sign byte {@link BigInteger#toByteArray()} may add.
     */
    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
