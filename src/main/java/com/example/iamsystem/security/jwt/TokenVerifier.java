package com.example.iamsystem.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.stereotype.Component;

import java.security.Key;

import static com.example.iamsystem.security.jwt.TokenIssuer.ACCESS_TOKEN_TYPE;
import static com.example.iamsystem.security.jwt.TokenIssuer.SESSION_ID;

/**
 * Reads access tokens issued by {@link TokenIssuer}. A token is accepted only when it is signed with RS256 by one of our
 * {@link SigningKeys} (found by its {@code kid}), is unexpired, and is typed {@code at+jwt}. Unsigned tokens and tokens
 * signed with any other algorithm, such as HMAC keyed with our public key, are rejected: the public key can only verify.
 * Whether the token's session is still live is the caller's check.
 */
@Component
public class TokenVerifier {

    private final SigningKeys signingKeys;
    private final JwtParser parser;

    public TokenVerifier(SigningKeys signingKeys) {
        this.signingKeys = signingKeys;
        this.parser = Jwts.parser().keyLocator(this::verificationKey).build();
    }

    /**
     * Verifies the signature, expiry and token type and returns the claims.
     *
     * @throws JwtException             if the token is malformed, not ours, expired or not an access token
     * @throws IllegalArgumentException if the token is blank
     */
    public Claims parseAccessToken(String token) {
        Jws<Claims> jws = parser.parseSignedClaims(token);
        if (!ACCESS_TOKEN_TYPE.equals(jws.getHeader().getType())) {
            throw new UnsupportedJwtException("Not an access token");
        }
        return jws.getPayload();
    }

    /**
     * @throws IllegalArgumentException if the subject isn't a user id
     */
    public Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /**
     * The session the token belongs to, or {@code null} if the token names none.
     */
    public Long sessionIdOf(Claims claims) {
        return claims.get(SESSION_ID, Long.class);
    }

    private Key verificationKey(Header header) {
        if (!(header instanceof ProtectedHeader protectedHeader)
                || !Jwts.SIG.RS256.getId().equals(protectedHeader.getAlgorithm())) {
            throw new UnsupportedJwtException("Only RS256-signed tokens are accepted");
        }
        return signingKeys.findPublicKey(protectedHeader.getKeyId())
                .orElseThrow(() -> new UnsupportedJwtException("Unknown signing key"));
    }
}
