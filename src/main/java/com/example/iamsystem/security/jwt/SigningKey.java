package com.example.iamsystem.security.jwt;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * An RSA key pair that signs tokens. Only the private key can sign; the public key, published in the JWKS, can only verify.
 */
public record SigningKey(String kid, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
}
