package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.security.config.JwtProperties;
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

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TokenIssuerTest {

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
        user.setUsername("alice");
        user.setFullName("Alice Example");
        user.setUserType(UserType.MANAGEMENT);
        user.setTenant(tenant);
        user.setVersion(3);

        client = new Client();
        client.setClientId("app-client");
        client.setAccessTokenTtlSeconds(300);
        client.setRefreshTokenTtlSeconds(7200);
        client.getTokenClaims().add(TokenClaim.USERNAME);
    }

    @Test
    @DisplayName("An access token is RS256-signed and names the signing key and the access-token type in its header")
    void accessToken_headerNamesKeyAndType() {
        Jws<Claims> jws = parse(issuer.issueAccessToken(user, client));

        assertEquals("RS256", jws.getHeader().getAlgorithm());
        assertEquals(signingKeys.current().kid(), jws.getHeader().getKeyId());
        assertEquals("at+jwt", jws.getHeader().getType());
    }

    @Test
    @DisplayName("An access token names the tenant as issuer, the user as subject and the client as audience")
    void accessToken_carriesIssuerSubjectAndAudience() {
        Claims claims = verifier.parseClaims(issuer.issueAccessToken(user, client), ACCESS_TOKEN);

        assertEquals("https://iam.test/tenants/100", claims.getIssuer());
        assertEquals("7", claims.getSubject());
        assertEquals(Set.of("app-client"), claims.getAudience());
        assertEquals("app-client", verifier.clientIdOf(claims));
        assertEquals(3, verifier.versionOf(claims));
    }

    @Test
    @DisplayName("Every token gets its own id")
    void tokens_haveUniqueIds() {
        Claims first = verifier.parseClaims(issuer.issueAccessToken(user, client), ACCESS_TOKEN);
        Claims second = verifier.parseClaims(issuer.issueAccessToken(user, client), ACCESS_TOKEN);

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("Access and refresh tokens live as long as the client's settings say")
    void tokens_liveAsLongAsTheClientSays() {
        Claims access = verifier.parseClaims(issuer.issueAccessToken(user, client), ACCESS_TOKEN);
        Claims refresh = verifier.parseClaims(issuer.issueRefreshToken(user, client), REFRESH_TOKEN);

        assertEquals(Duration.ofSeconds(300), lifetime(access));
        assertEquals(Duration.ofSeconds(7200), lifetime(refresh));
    }

    @Test
    @DisplayName("An access token carries exactly the user claims the client selected; a refresh token carries none")
    void accessToken_carriesTheSelectedClaims() {
        client.getTokenClaims().addAll(Set.of(TokenClaim.FULL_NAME, TokenClaim.USER_TYPE, TokenClaim.TENANT_ID));

        Claims access = verifier.parseClaims(issuer.issueAccessToken(user, client), ACCESS_TOKEN);
        Claims refresh = verifier.parseClaims(issuer.issueRefreshToken(user, client), REFRESH_TOKEN);

        assertEquals("alice", access.get("username"));
        assertEquals("Alice Example", access.get("full_name"));
        assertEquals("MANAGEMENT", access.get("user_type"));
        assertEquals(100, access.get("tenant_id", Integer.class));
        assertFalse(access.containsKey("email"));
        assertFalse(refresh.containsKey("username"));
    }

    @Test
    @DisplayName("A selected claim the user has no value for is left out")
    void accessToken_leavesOutEmptyClaims() {
        client.getTokenClaims().add(TokenClaim.EMAIL);

        Claims access = verifier.parseClaims(issuer.issueAccessToken(user, client), ACCESS_TOKEN);

        assertFalse(access.containsKey("email"));
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(signingKeys.current().publicKey()).build().parseSignedClaims(token);
    }

    private static Duration lifetime(Claims claims) {
        return Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
    }
}
