package com.example.iamsystem.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static com.example.iamsystem.common.util.SecretMask.MASK;

/**
 * Token signing ({@code iam.jwt.*}). {@code issuer} is the public base URL of this IAM; a token's {@code iss} is
 * {@code <issuer>/tenants/<tenant id>}. {@code keys} are the RSA signing keys: the first one signs new tokens and every one
 * of them is published in the JWKS, so an old key stays listed until the tokens it signed have expired. A key whose
 * settings are all empty counts as no key (the profile files name {@code keys[0]} with empty defaults). Without any key
 * the startup stops, unless {@code allowTemporaryKey} (the local profile) lets a temporary one be generated.
 */
@Validated
@ConfigurationProperties("iam.jwt")
public record JwtProperties(@DefaultValue("http://localhost:8080") @NotBlank String issuer,
                            @DefaultValue List<@Valid SigningKeyProperties> keys,
                            boolean allowTemporaryKey) {

    public JwtProperties {
        keys = keys.stream().filter(key -> !key.isUnset()).toList();
    }

    /**
     * @param kid                the key id published in the JWKS and put into every token header
     * @param privateKey         a PKCS#8 RSA private key (at least 2048 bits) in PEM, header and footer lines optional
     * @param privateKeyLocation where to read that PEM instead, e.g. {@code file:/run/secrets/jwt-signing-key.pem}
     */
    public record SigningKeyProperties(@NotBlank String kid, String privateKey, String privateKeyLocation) {

        @AssertTrue(message = "needs either private-key or private-key-location, not both")
        public boolean isOneKeySource() {
            return isPresent(privateKey) != isPresent(privateKeyLocation);
        }

        public boolean isInline() {
            return isPresent(privateKey);
        }

        boolean isUnset() {
            return !isPresent(kid) && !isPresent(privateKey) && !isPresent(privateKeyLocation);
        }

        /**
         * Never shows the key; the location is no secret.
         */
        @Override
        public String toString() {
            return "SigningKeyProperties[kid=" + kid + ", privateKey=" + MASK + ", privateKeyLocation=" + privateKeyLocation + "]";
        }

        private static boolean isPresent(String value) {
            return value != null && !value.isBlank();
        }
    }
}
