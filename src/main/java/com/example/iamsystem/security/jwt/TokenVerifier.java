package com.example.iamsystem.security.jwt;

import com.example.iamsystem.user.entity.User;
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

import static com.example.iamsystem.security.jwt.TokenIssuer.CLIENT_ID;
import static com.example.iamsystem.security.jwt.TokenIssuer.VERSION;

/**
 * Reads tokens issued by {@link TokenIssuer}. A token is accepted only when it is signed with RS256 by one of our
 * {@link SigningKeys} (found by its {@code kid}), is unexpired, and has the expected {@code typ}. Unsigned tokens and tokens
 * signed with any other algorithm, such as HMAC keyed with our public key, are rejected: the public key can only verify.
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
     * @throws JwtException             if the token is malformed, not ours, expired or of the other type
     * @throws IllegalArgumentException if the token is blank
     */
    public Claims parseClaims(String token, TokenType tokenType) {
        Jws<Claims> jws = parser.parseSignedClaims(token);
        if (!tokenType.headerType().equals(jws.getHeader().getType())) {
            throw new UnsupportedJwtException("Not a token of type " + tokenType.headerType());
        }
        return jws.getPayload();
    }

    /**
     * @throws IllegalArgumentException if the subject isn't a user id
     */
    public Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String clientIdOf(Claims claims) {
        return claims.get(CLIENT_ID, String.class);
    }

    /**
     * The user version the token was issued for, or {@code null} if the token carries none.
     */
    public Integer versionOf(Claims claims) {
        return claims.get(VERSION, Integer.class);
    }

    /**
     * Whether verified claims were issued for this user and its current version (not revoked since).
     */
    public boolean isIssuedFor(Claims claims, User user) {
        return String.valueOf(user.getId()).equals(claims.getSubject())
                && Integer.valueOf(user.getVersion()).equals(versionOf(claims));
    }

    /**
     * Checks signature, expiry and type only.
     */
    public boolean isValid(String token, TokenType tokenType) {
        try {
            parseClaims(token, tokenType);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
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
