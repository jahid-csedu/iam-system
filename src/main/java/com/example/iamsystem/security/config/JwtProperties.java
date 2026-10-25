package com.example.iamsystem.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Token signing ({@code iam.jwt.*}). {@code issuer} is the public base URL of this IAM; a token's {@code iss} is
 * {@code <issuer>/tenants/<tenant id>}. {@code keys} are the RSA signing keys: the first one signs new tokens and every one
 * of them is published in the JWKS, so an old key stays listed until the tokens it signed have expired.
 * Without any key a temporary one is generated at startup.
 */
@Validated
@ConfigurationProperties("iam.jwt")
public record JwtProperties(@DefaultValue("http://localhost:8080") @NotBlank String issuer,
                            @DefaultValue List<@Valid SigningKeyProperties> keys) {

    /**
     * @param kid        the key id published in the JWKS and put into every token header
     * @param privateKey a PKCS#8 RSA private key (at least 2048 bits) in PEM, header and footer lines optional
     */
    public record SigningKeyProperties(@NotBlank String kid, @NotBlank String privateKey) {
    }
}
