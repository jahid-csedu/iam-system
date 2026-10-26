package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TokenIssuerTest {

    private final JwtProperties properties = new JwtProperties("https://iam.test", List.of(), true);
    private final SigningKeys signingKeys = new SigningKeys(properties, new DefaultResourceLoader());
    private final TokenIssuer issuer = new TokenIssuer(signingKeys, properties);
    private final TokenVerifier verifier = new TokenVerifier(signingKeys);
    private Client client;
    private UserSession session;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setFullName("Alice Example");
        user.setUserType(UserType.MANAGEMENT);
        user.setTenant(tenant);

        client = new Client();
        client.setClientId("app-client");
        client.setAccessTokenTtlSeconds(300);
        client.getTokenClaims().add(TokenClaim.USERNAME);

        session = new UserSession();
        session.setId(42L);
        session.setUser(user);
        session.setClient(client);
    }

    @Test
    @DisplayName("An access token is RS256-signed and names the signing key and the access-token type in its header")
    void accessToken_headerNamesKeyAndType() {
        Jws<Claims> jws = Jwts.parser().verifyWith(signingKeys.current().publicKey()).build()
                .parseSignedClaims(issuer.issueAccessToken(session).token());

        assertEquals("RS256", jws.getHeader().getAlgorithm());
        assertEquals(signingKeys.current().kid(), jws.getHeader().getKeyId());
        assertEquals("at+jwt", jws.getHeader().getType());
    }

    @Test
    @DisplayName("An access token names the tenant as issuer, the user as subject, the client as audience and its session")
    void accessToken_carriesIssuerSubjectAudienceAndSession() {
        Claims claims = verifier.parseAccessToken(issuer.issueAccessToken(session).token());

        assertEquals("https://iam.test/tenants/100", claims.getIssuer());
        assertEquals(7L, verifier.userIdOf(claims));
        assertEquals(Set.of("app-client"), claims.getAudience());
        assertEquals("app-client", claims.get("client_id"));
        assertEquals(42L, verifier.sessionIdOf(claims));
        assertFalse(claims.containsKey("version"));
    }

    @Test
    @DisplayName("Every token gets its own id")
    void tokens_haveUniqueIds() {
        Claims first = verifier.parseAccessToken(issuer.issueAccessToken(session).token());
        Claims second = verifier.parseAccessToken(issuer.issueAccessToken(session).token());

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("An access token lives as long as the client's settings say, and its expires_in says the same")
    void accessToken_livesAsLongAsTheClientSays() {
        IssuedAccessToken issued = issuer.issueAccessToken(session);
        Claims claims = verifier.parseAccessToken(issued.token());

        assertEquals(300, issued.expiresIn());
        assertEquals(Duration.ofSeconds(300),
                Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()));
    }

    @Test
    @DisplayName("An access token carries exactly the user claims the client selected")
    void accessToken_carriesTheSelectedClaims() {
        client.getTokenClaims().addAll(Set.of(TokenClaim.FULL_NAME, TokenClaim.USER_TYPE, TokenClaim.TENANT_ID));

        Claims claims = verifier.parseAccessToken(issuer.issueAccessToken(session).token());

        assertEquals("alice", claims.get("username"));
        assertEquals("Alice Example", claims.get("full_name"));
        assertEquals("MANAGEMENT", claims.get("user_type"));
        assertEquals(100, claims.get("tenant_id", Integer.class));
        assertFalse(claims.containsKey("email"));
    }

    @Test
    @DisplayName("A selected claim the user has no value for is left out")
    void accessToken_leavesOutEmptyClaims() {
        client.getTokenClaims().add(TokenClaim.EMAIL);

        Claims claims = verifier.parseAccessToken(issuer.issueAccessToken(session).token());

        assertFalse(claims.containsKey("email"));
    }
}
