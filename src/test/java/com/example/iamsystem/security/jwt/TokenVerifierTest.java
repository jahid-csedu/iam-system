package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.security.config.JwtProperties.SigningKeyProperties;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.example.iamsystem.security.jwt.TestKeys.configuredKey;
import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenVerifierTest {

    private final JwtProperties properties = new JwtProperties("https://iam.test", List.of());
    private final SigningKeys signingKeys = new SigningKeys(properties);
    private final TokenIssuer issuer = new TokenIssuer(signingKeys, properties);
    private final TokenVerifier verifier = new TokenVerifier(signingKeys);
    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        user = new User();
        user.setId(7L);
        user.setTenant(tenant);
        user.setVersion(3);

        client = new Client();
        client.setClientId("app-client");
        client.setAccessTokenTtlSeconds(300);
        client.setRefreshTokenTtlSeconds(7200);
    }

    @Test
    @DisplayName("A token we issued is valid")
    void issuedToken_isValid() {
        assertTrue(verifier.isValid(issuer.issueAccessToken(user, client), ACCESS_TOKEN));
    }

    @Test
    @DisplayName("A token signed with HMAC using our public key as the secret is rejected")
    void hmacTokenKeyedWithThePublicKey_isRejected() {
        SigningKey key = signingKeys.current();
        String forged = Jwts.builder()
                .header().keyId(key.kid()).type("at+jwt").and()
                .subject("7")
                .signWith(Keys.hmacShaKeyFor(key.publicKey().getEncoded()), Jwts.SIG.HS256)
                .compact();

        assertThrows(JwtException.class, () -> verifier.parseClaims(forged, ACCESS_TOKEN));
    }

    @Test
    @DisplayName("An unsigned token is rejected")
    void unsignedToken_isRejected() {
        String unsigned = Jwts.builder().header().type("at+jwt").and().subject("7").compact();

        assertThrows(JwtException.class, () -> verifier.parseClaims(unsigned, ACCESS_TOKEN));
    }

    @Test
    @DisplayName("A token signed with a key that isn't ours is rejected, even under one of our key ids")
    void tokenSignedWithAnotherKey_isRejected() {
        SigningKey foreign = new SigningKeys(new JwtProperties("https://iam.test", List.of(configuredKey("foreign")))).current();
        String underOurKid = Jwts.builder()
                .header().keyId(signingKeys.current().kid()).type("at+jwt").and()
                .subject("7")
                .signWith(foreign.privateKey(), Jwts.SIG.RS256)
                .compact();
        String underItsOwnKid = Jwts.builder()
                .header().keyId(foreign.kid()).type("at+jwt").and()
                .subject("7")
                .signWith(foreign.privateKey(), Jwts.SIG.RS256)
                .compact();

        assertThrows(JwtException.class, () -> verifier.parseClaims(underOurKid, ACCESS_TOKEN));
        assertThrows(JwtException.class, () -> verifier.parseClaims(underItsOwnKid, ACCESS_TOKEN));
    }

    @Test
    @DisplayName("A token signed with an older key that is still configured stays valid")
    void tokenOfAnOlderConfiguredKey_staysValid() {
        SigningKeyProperties old = configuredKey("old");
        JwtProperties oldConfig = new JwtProperties("https://iam.test", List.of(old));
        String token = new TokenIssuer(new SigningKeys(oldConfig), oldConfig).issueAccessToken(user, client);
        SigningKeys rotated = new SigningKeys(new JwtProperties("https://iam.test", List.of(configuredKey("new"), old)));

        assertTrue(new TokenVerifier(rotated).isValid(token, ACCESS_TOKEN));
    }

    @Test
    @DisplayName("Access and refresh tokens can't be used as each other")
    void tokens_cannotBeUsedAsTheOtherType() {
        String refreshToken = issuer.issueRefreshToken(user, client);
        String accessToken = issuer.issueAccessToken(user, client);

        assertFalse(verifier.isValid(refreshToken, ACCESS_TOKEN));
        assertFalse(verifier.isValid(accessToken, REFRESH_TOKEN));
        assertTrue(verifier.isValid(refreshToken, REFRESH_TOKEN));
    }

    @Test
    @DisplayName("An expired token is rejected")
    void expiredToken_isRejected() {
        SigningKey key = signingKeys.current();
        String expired = Jwts.builder()
                .header().keyId(key.kid()).type("at+jwt").and()
                .subject("7")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key.privateKey(), Jwts.SIG.RS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> verifier.parseClaims(expired, ACCESS_TOKEN));
    }

    @Test
    @DisplayName("A malformed or blank token is not valid")
    void malformedToken_isNotValid() {
        assertFalse(verifier.isValid("not-a-jwt", ACCESS_TOKEN));
        assertFalse(verifier.isValid("", ACCESS_TOKEN));
    }

    @Test
    @DisplayName("A token is issued for the user only while the user's version is unchanged")
    void isIssuedFor_requiresSameUserAndVersion() {
        Claims claims = verifier.parseClaims(issuer.issueRefreshToken(user, client), REFRESH_TOKEN);
        assertTrue(verifier.isIssuedFor(claims, user));

        user.setVersion(4);
        assertFalse(verifier.isIssuedFor(claims, user));

        User other = new User();
        other.setId(8L);
        other.setVersion(3);
        assertFalse(verifier.isIssuedFor(claims, other));
    }

    @Test
    @DisplayName("A subject that isn't a user id is rejected")
    void userIdOf_nonNumericSubject_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> verifier.userIdOf(Jwts.claims().subject("alice").build()));
    }

    @Test
    @DisplayName("A token without a version has no version")
    void versionOf_missingClaim_isNull() {
        assertNull(verifier.versionOf(Jwts.claims().subject("7").build()));
        assertEquals(7L, verifier.userIdOf(Jwts.claims().subject("7").build()));
    }
}
