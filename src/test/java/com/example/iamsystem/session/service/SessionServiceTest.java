package com.example.iamsystem.session.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.common.exception.InvalidTokenException;
import com.example.iamsystem.session.entity.RefreshToken;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.session.repository.RefreshTokenRepository;
import com.example.iamsystem.session.repository.UserSessionRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();
    private SessionService service;
    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessionRepository, refreshTokenRepository, generator);
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        user = new User();
        user.setId(7L);
        user.setTenant(tenant);
        client = new Client();
        client.setId(3L);
        client.setAccessTokenTtlSeconds(300);
        client.setRefreshTokenEnabled(true);
        client.setRefreshTokenTtlSeconds(7200);
    }

    @Test
    @DisplayName("A login session lives as long as its refresh token, and only the token's hash is stored")
    void start_withRefreshTokens_storesOnlyTheHash() {
        IssuedSession issued = service.start(user, client);

        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(stored.capture());
        assertNotNull(issued.refreshToken());
        assertEquals(generator.hash(issued.refreshToken()), stored.getValue().getTokenHash());
        assertEquals(Duration.ofSeconds(7200), Duration.between(stored.getValue().getCreatedAt(), stored.getValue().getExpiresAt()));
        UserSession session = issued.session();
        assertSame(user, session.getUser());
        assertSame(user.getTenant(), session.getTenant());
        assertEquals(Duration.ofSeconds(7200), Duration.between(session.getCreatedAt(), session.getExpiresAt()));
    }

    @Test
    @DisplayName("Without refresh tokens a session lives as long as its access token")
    void start_withoutRefreshTokens_livesAsLongAsTheAccessToken() {
        client.setRefreshTokenEnabled(false);

        IssuedSession issued = service.start(user, client);

        assertNull(issued.refreshToken());
        assertEquals(Duration.ofSeconds(300),
                Duration.between(issued.session().getCreatedAt(), issued.session().getExpiresAt()));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("An unused, unexpired refresh token of a live session of the same client is refreshable")
    void requireRefreshableToken_acceptsAUsableToken() {
        RefreshToken token = givenStoredToken("token", liveSession(), Instant.now().plusSeconds(60));

        assertSame(token, service.requireRefreshableToken(client, "token"));
    }

    @Test
    @DisplayName("A refresh token presented through another client is refused without ending its session")
    void requireRefreshableToken_ofAnotherClient_isRefused() {
        givenStoredToken("token", liveSession(), Instant.now().plusSeconds(60));
        Client other = new Client();
        other.setId(4L);

        assertThrows(InvalidTokenException.class, () -> service.requireRefreshableToken(other, "token"));
        verify(sessionRepository, never()).revoke(anyLong(), any());
    }

    @Test
    @DisplayName("A used refresh token is refused and ends its session")
    void requireRefreshableToken_alreadyUsed_revokesTheSession() {
        RefreshToken token = givenStoredToken("token", liveSession(), Instant.now().plusSeconds(60));
        token.setUsedAt(Instant.now().minusSeconds(5));

        assertThrows(InvalidTokenException.class, () -> service.requireRefreshableToken(client, "token"));
        verify(sessionRepository).revoke(eq(10L), any(Instant.class));
    }

    @Test
    @DisplayName("An expired refresh token, or one of a revoked session, is refused")
    void requireRefreshableToken_expiredOrRevoked_isRefused() {
        givenStoredToken("expired", liveSession(), Instant.now().minusSeconds(1));
        UserSession revoked = liveSession();
        revoked.setRevokedAt(Instant.now().minusSeconds(5));
        givenStoredToken("revoked", revoked, Instant.now().plusSeconds(60));

        assertThrows(InvalidTokenException.class, () -> service.requireRefreshableToken(client, "expired"));
        assertThrows(InvalidTokenException.class, () -> service.requireRefreshableToken(client, "revoked"));
    }

    @Test
    @DisplayName("An unknown refresh token is refused")
    void requireRefreshableToken_unknown_isRefused() {
        when(refreshTokenRepository.findByTokenHash(generator.hash("nope"))).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> service.requireRefreshableToken(client, "nope"));
    }

    @Test
    @DisplayName("Rotation uses up the token, issues a successor and extends the session")
    void rotate_issuesASuccessorAndExtendsTheSession() {
        UserSession session = liveSession();
        session.setExpiresAt(Instant.now().plusSeconds(10));
        RefreshToken token = refreshToken(session, Instant.now().plusSeconds(10));
        when(refreshTokenRepository.markUsed(eq(5L), any(Instant.class))).thenReturn(1);

        IssuedSession rotated = service.rotate(token);

        assertNotNull(rotated.refreshToken());
        assertTrue(session.getExpiresAt().isAfter(Instant.now().plusSeconds(7100)));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("When a concurrent refresh used the token first, the session ends")
    void rotate_lostRace_revokesTheSession() {
        RefreshToken token = refreshToken(liveSession(), Instant.now().plusSeconds(60));
        when(refreshTokenRepository.markUsed(eq(5L), any(Instant.class))).thenReturn(0);

        assertThrows(InvalidTokenException.class, () -> service.rotate(token));
        verify(sessionRepository).revoke(eq(10L), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    private UserSession liveSession() {
        UserSession session = new UserSession();
        session.setId(10L);
        session.setUser(user);
        session.setClient(client);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        return session;
    }

    private RefreshToken givenStoredToken(String token, UserSession session, Instant expiresAt) {
        RefreshToken refreshToken = refreshToken(session, expiresAt);
        when(refreshTokenRepository.findByTokenHash(generator.hash(token))).thenReturn(Optional.of(refreshToken));
        return refreshToken;
    }

    private static RefreshToken refreshToken(UserSession session, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(5L);
        refreshToken.setSession(session);
        refreshToken.setExpiresAt(expiresAt);
        return refreshToken;
    }
}
