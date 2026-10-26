package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.security.config.JwtProperties.SigningKeyProperties;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.example.iamsystem.security.jwt.TestKeys.configuredKey;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenVerifierTest {

    private static final DefaultResourceLoader RESOURCES = new DefaultResourceLoader();

    private final JwtProperties properties = new JwtProperties("https://iam.test", List.of(), true);
    private final SigningKeys signingKeys = new SigningKeys(properties, RESOURCES);
    private final TokenIssuer issuer = new TokenIssuer(signingKeys, properties);
    private final TokenVerifier verifier = new TokenVerifier(signingKeys);
    private UserSession session;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        User user = new User();
        user.setId(7L);
        user.setTenant(tenant);
        Client client = new Client();
        client.setClientId("app-client");
        client.setAccessTokenTtlSeconds(300);
        session = new UserSession();
        session.setId(42L);
        session.setUser(user);
        session.setClient(client);
    }

    @Test
    @DisplayName("A token we issued is accepted")
    void issuedToken_isAccepted() {
        assertDoesNotThrow(() -> verifier.parseAccessToken(issuer.issueAccessToken(session).token()));
    }

    @Test
    @DisplayName("A token signed with HMAC using our public key as the secret is rejected")
    void hmacTokenKeyedWithThePublicKey_isRejected() {
        SigningKey key = signingKeys.current();
        String forged = accessTokenClaims(key.kid())
                .signWith(Keys.hmacShaKeyFor(key.publicKey().getEncoded()), Jwts.SIG.HS256)
                .compact();

        assertThrows(JwtException.class, () -> verifier.parseAccessToken(forged));
    }

    @Test
    @DisplayName("An unsigned token is rejected")
    void unsignedToken_isRejected() {
        String unsigned = accessTokenClaims(signingKeys.current().kid()).compact();

        assertThrows(JwtException.class, () -> verifier.parseAccessToken(unsigned));
    }

    @Test
    @DisplayName("A token signed with a key that isn't ours is rejected, even under one of our key ids")
    void tokenSignedWithAnotherKey_isRejected() {
        SigningKey foreign = new SigningKeys(new JwtProperties("https://iam.test", List.of(configuredKey("foreign")), false), RESOURCES).current();
        String underOurKid = accessTokenClaims(signingKeys.current().kid()).signWith(foreign.privateKey(), Jwts.SIG.RS256).compact();
        String underItsOwnKid = accessTokenClaims(foreign.kid()).signWith(foreign.privateKey(), Jwts.SIG.RS256).compact();

        assertThrows(JwtException.class, () -> verifier.parseAccessToken(underOurKid));
        assertThrows(JwtException.class, () -> verifier.parseAccessToken(underItsOwnKid));
    }

    @Test
    @DisplayName("A token signed with an older key that is still configured stays valid")
    void tokenOfAnOlderConfiguredKey_staysValid() {
        SigningKeyProperties old = configuredKey("old");
        JwtProperties oldConfig = new JwtProperties("https://iam.test", List.of(old), false);
        String token = new TokenIssuer(new SigningKeys(oldConfig, RESOURCES), oldConfig).issueAccessToken(session).token();
        SigningKeys rotated = new SigningKeys(new JwtProperties("https://iam.test", List.of(configuredKey("new"), old), false), RESOURCES);

        assertDoesNotThrow(() -> new TokenVerifier(rotated).parseAccessToken(token));
    }

    @Test
    @DisplayName("A signed token of another type is not an access token")
    void tokenOfAnotherType_isRejected() {
        SigningKey key = signingKeys.current();
        String other = Jwts.builder().header().keyId(key.kid()).type("JWT").and()
                .subject("7")
                .signWith(key.privateKey(), Jwts.SIG.RS256)
                .compact();

        assertThrows(JwtException.class, () -> verifier.parseAccessToken(other));
    }

    @Test
    @DisplayName("An expired token is rejected")
    void expiredToken_isRejected() {
        SigningKey key = signingKeys.current();
        String expired = accessTokenClaims(key.kid())
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key.privateKey(), Jwts.SIG.RS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> verifier.parseAccessToken(expired));
    }

    @Test
    @DisplayName("A malformed or blank token is rejected")
    void malformedToken_isRejected() {
        assertThrows(JwtException.class, () -> verifier.parseAccessToken("not-a-jwt"));
        assertThrows(IllegalArgumentException.class, () -> verifier.parseAccessToken(""));
    }

    @Test
    @DisplayName("A subject that isn't a user id is rejected")
    void userIdOf_nonNumericSubject_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> verifier.userIdOf(Jwts.claims().subject("alice").build()));
    }

    @Test
    @DisplayName("A token without a session has no session id")
    void sessionIdOf_missingClaim_isNull() {
        assertNull(verifier.sessionIdOf(Jwts.claims().subject("7").build()));
    }

    private static JwtBuilder accessTokenClaims(String kid) {
        return Jwts.builder().header().keyId(kid).type("at+jwt").and().subject("7");
    }
}
