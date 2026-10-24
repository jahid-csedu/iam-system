package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenUtilTest {

    private final JwtTokenUtil tokenUtil = new JwtTokenUtil();
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
    @DisplayName("An access token carries the user id as subject, the client and the user's version")
    void accessToken_carriesUserIdClientAndVersion() {
        Claims claims = tokenUtil.parseClaims(tokenUtil.generateAccessToken(user, client), ACCESS_TOKEN);

        assertEquals("7", claims.getSubject());
        assertEquals(7L, tokenUtil.userIdOf(claims));
        assertEquals("app-client", tokenUtil.clientIdOf(claims));
        assertEquals(3, tokenUtil.versionOf(claims));
        assertFalse(claims.containsKey("authorities"));
    }

    @Test
    @DisplayName("Access and refresh tokens live as long as the client's settings say")
    void tokens_liveAsLongAsTheClientSays() {
        Claims access = tokenUtil.parseClaims(tokenUtil.generateAccessToken(user, client), ACCESS_TOKEN);
        Claims refresh = tokenUtil.parseClaims(tokenUtil.generateRefreshToken(user, client), REFRESH_TOKEN);

        assertEquals(Duration.ofSeconds(300), lifetime(access));
        assertEquals(Duration.ofSeconds(7200), lifetime(refresh));
    }

    @Test
    @DisplayName("An access token carries exactly the user claims the client selected; a refresh token carries none")
    void accessToken_carriesTheSelectedClaims() {
        client.getTokenClaims().addAll(Set.of(TokenClaim.FULL_NAME, TokenClaim.USER_TYPE, TokenClaim.TENANT_ID));

        Claims access = tokenUtil.parseClaims(tokenUtil.generateAccessToken(user, client), ACCESS_TOKEN);
        Claims refresh = tokenUtil.parseClaims(tokenUtil.generateRefreshToken(user, client), REFRESH_TOKEN);

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

        Claims access = tokenUtil.parseClaims(tokenUtil.generateAccessToken(user, client), ACCESS_TOKEN);

        assertFalse(access.containsKey("email"));
    }

    @Test
    @DisplayName("Access and refresh tokens are signed with different keys")
    void tokens_cannotBeUsedAsTheOtherType() {
        String refreshToken = tokenUtil.generateRefreshToken(user, client);

        assertThrows(JwtException.class, () -> tokenUtil.parseClaims(refreshToken, ACCESS_TOKEN));
        assertFalse(tokenUtil.isValid(refreshToken, ACCESS_TOKEN));
        assertTrue(tokenUtil.isValid(refreshToken, REFRESH_TOKEN));
    }

    @Test
    @DisplayName("A malformed or blank token is not valid")
    void isValid_malformedToken_isFalse() {
        assertFalse(tokenUtil.isValid("not-a-jwt", ACCESS_TOKEN));
        assertFalse(tokenUtil.isValid("", ACCESS_TOKEN));
    }

    @Test
    @DisplayName("A token is issued for the user only while the user's version is unchanged")
    void isIssuedFor_requiresSameUserAndVersion() {
        Claims claims = tokenUtil.parseClaims(tokenUtil.generateRefreshToken(user, client), REFRESH_TOKEN);
        assertTrue(tokenUtil.isIssuedFor(claims, user));

        user.setVersion(4);
        assertFalse(tokenUtil.isIssuedFor(claims, user));

        User other = new User();
        other.setId(8L);
        other.setVersion(3);
        assertFalse(tokenUtil.isIssuedFor(claims, other));
    }

    @Test
    @DisplayName("A subject that isn't a user id is rejected")
    void userIdOf_nonNumericSubject_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> tokenUtil.userIdOf(Jwts.claims().subject("alice").build()));
    }

    @Test
    @DisplayName("A token without a version has no version")
    void versionOf_missingClaim_isNull() {
        assertNull(tokenUtil.versionOf(Jwts.claims().subject("alice").build()));
    }

    private static Duration lifetime(Claims claims) {
        return Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
    }
}
