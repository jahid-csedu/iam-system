package com.example.iamsystem.auth.service;

import com.example.iamsystem.auth.dto.IntrospectionRequest;
import com.example.iamsystem.auth.dto.IntrospectionResponse;
import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.security.jwt.SigningKeys;
import com.example.iamsystem.security.jwt.TokenIssuer;
import com.example.iamsystem.security.jwt.TokenVerifier;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.session.repository.UserSessionRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenIntrospectionServiceTest {

    private final JwtProperties properties = new JwtProperties("https://iam.test", List.of());
    private final SigningKeys signingKeys = new SigningKeys(properties);
    private final TokenIssuer tokenIssuer = new TokenIssuer(signingKeys, properties);

    @Mock
    private UserSessionRepository sessionRepository;

    private TokenIntrospectionService service;
    private UserSession session;

    @BeforeEach
    void setUp() {
        service = new TokenIntrospectionService(new TokenVerifier(signingKeys), sessionRepository);
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setTenant(tenant);
        Client client = new Client();
        client.setClientId("app-client");
        client.setAccessTokenTtlSeconds(300);
        client.getTokenClaims().add(TokenClaim.USERNAME);
        session = new UserSession();
        session.setId(42L);
        session.setUser(user);
        session.setClient(client);
    }

    @Test
    @DisplayName("A valid token of a live session is active and comes with its claims")
    void liveToken_isActiveWithItsClaims() {
        when(sessionRepository.findActor(eq(42L), eq(7L), any(Instant.class)))
                .thenReturn(Optional.of(new AuthenticatedActor(7L, "alice", 100L, false, 42L)));

        IntrospectionResponse response = service.introspect(new IntrospectionRequest(tokenIssuer.issueAccessToken(session).token()));

        assertTrue(response.isActive());
        assertEquals("7", response.getClaims().get("sub"));
        assertEquals("https://iam.test/tenants/100", response.getClaims().get("iss"));
        assertEquals("app-client", response.getClaims().get("client_id"));
        assertEquals(Set.of("app-client"), response.getClaims().get("aud"));
        assertEquals("alice", response.getClaims().get("username"));
        assertEquals("Bearer", response.getClaims().get("token_type"));
        long iat = (Long) response.getClaims().get("iat");
        assertEquals(300L, (Long) response.getClaims().get("exp") - iat);
    }

    @Test
    @DisplayName("A valid token whose session ended, or whose user or tenant can't log in, is inactive without claims")
    void tokenWithoutLiveSession_isInactive() {
        when(sessionRepository.findActor(eq(42L), eq(7L), any(Instant.class))).thenReturn(Optional.empty());

        IntrospectionResponse response = service.introspect(new IntrospectionRequest(tokenIssuer.issueAccessToken(session).token()));

        assertFalse(response.isActive());
        assertTrue(response.getClaims().isEmpty());
    }

    @Test
    @DisplayName("A malformed or forged token is inactive and never reaches the database")
    void malformedToken_isInactive() {
        IntrospectionResponse response = service.introspect(new IntrospectionRequest("not-a-jwt"));

        assertFalse(response.isActive());
        verifyNoInteractions(sessionRepository);
    }
}
