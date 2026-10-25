package com.example.iamsystem.security.jwt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Reads a configured signing key: a PKCS#8 RSA private key in PEM. The public key is derived from it, so only the
 * private key has to be configured.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RsaKeyParser {

    static final int MIN_KEY_BITS = 2048;
    static final String RSA = "RSA";
    private static final String PEM_BOUNDARY_LINE = "-----[^-]+-----";
    private static final String WHITESPACE = "\\s";

    /**
     * @throws IllegalStateException if the text isn't a PKCS#8 RSA private key of at least {@value #MIN_KEY_BITS} bits;
     *                               the message never contains the key
     */
    static SigningKey parse(String kid, String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(pem.replaceAll(PEM_BOUNDARY_LINE, "").replaceAll(WHITESPACE, ""));
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            if (!(keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der)) instanceof RSAPrivateCrtKey privateKey)) {
                throw new IllegalStateException("JWT signing key '" + kid + "' has no public exponent");
            }
            if (privateKey.getModulus().bitLength() < MIN_KEY_BITS) {
                throw new IllegalStateException("JWT signing key '" + kid + "' is shorter than " + MIN_KEY_BITS + " bits");
            }
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new SigningKey(kid, privateKey, publicKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("JWT signing key '" + kid + "' is not a PKCS#8 RSA private key in PEM");
        }
    }
}
