package com.example.iamsystem.auth.service;

import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.ClientType;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.InvalidClientException;
import com.example.iamsystem.common.exception.InvalidTokenException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.jwt.TokenIssuer;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.security.userdetails.DefaultUserDetailsService;
import com.example.iamsystem.session.entity.RefreshToken;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.session.service.IssuedSession;
import com.example.iamsystem.session.service.SessionService;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String CONSOLE_ID = "console-client-id-000001";
    private static final String APP_ID = "app-client-id-0000000001";

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private DefaultUserDetailsService userDetailsService;
    @Mock
    private ClientResolver clientResolver;
    @Mock
    private SessionService sessionService;
    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AuthenticationService service;

    private Client console;
    private Client app;
    private User manager;
    private User regular;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        console = client(CONSOLE_ID, ClientType.MANAGEMENT_CONSOLE, tenant);
        app = client(APP_ID, ClientType.APPLICATION, tenant);
        manager = user("alice", UserType.MANAGEMENT);
        regular = user("carol", UserType.REGULAR);
    }

    @Test
    @DisplayName("Login authenticates the username inside the client's tenant and starts a session for that client")
    void login_authenticatesInsideTheClientsTenant() {
        when(clientResolver.resolve(CONSOLE_ID)).thenReturn(console);
        givenAuthenticated(manager);
        givenSessionStarted(manager, console, "refresh-token");

        JwtResponse response = service.login(new UserLoginDto(CONSOLE_ID, "alice", "Password1!"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> request = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(request.capture());
        assertEquals("alice", request.getValue().getName());
        assertEquals(100L, request.getValue().getDetails());
        assertTokens(response, "alice", "refresh-token");
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("A regular user can log in through an application client")
    void login_regularUserThroughApplication_isAllowed() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        givenAuthenticated(regular);
        givenSessionStarted(regular, app, "refresh-token");

        assertTokens(service.login(new UserLoginDto(APP_ID, "carol", "Password1!")), "carol", "refresh-token");
    }

    @Test
    @DisplayName("The management console refuses regular users")
    void login_regularUserThroughConsole_isDenied() {
        when(clientResolver.resolve(CONSOLE_ID)).thenReturn(console);
        givenAuthenticated(regular);

        assertThrows(NoAccessException.class, () -> service.login(new UserLoginDto(CONSOLE_ID, "carol", "Password1!")));
        verifyNoInteractions(sessionService, tokenIssuer);
    }

    @Test
    @DisplayName("An unknown client_id is rejected before any authentication")
    void login_unknownClient_isRejected() {
        when(clientResolver.resolve("nope")).thenThrow(new InvalidClientException("Unknown client_id"));

        assertThrows(InvalidClientException.class, () -> service.login(new UserLoginDto("nope", "alice", "Password1!")));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("Login with bad credentials starts no session")
    void login_withBadCredentials_startsNoSession() {
        when(clientResolver.resolve(CONSOLE_ID)).thenReturn(console);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> service.login(new UserLoginDto(CONSOLE_ID, "alice", "wrong")));
        verifyNoInteractions(sessionService, tokenIssuer);
    }

    @Test
    @DisplayName("A client without refresh tokens issues only an access token at login")
    void login_clientWithoutRefreshTokens_issuesNoRefreshToken() {
        app.setRefreshTokenEnabled(false);
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        givenAuthenticated(regular);
        givenSessionStarted(regular, app, null);

        JwtResponse response = service.login(new UserLoginDto(APP_ID, "carol", "Password1!"));

        assertEquals("access-token", response.getAccessToken());
        assertNull(response.getRefreshToken());
    }

    @Test
    @DisplayName("A usable refresh token is rotated: a new access token and a new refresh token are issued")
    void refresh_rotatesTheRefreshToken() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        RefreshToken refreshToken = refreshTokenOf(session(manager, app));
        when(sessionService.requireRefreshableToken(app, "old-refresh")).thenReturn(refreshToken);
        when(sessionService.rotate(refreshToken)).thenReturn(new IssuedSession(refreshToken.getSession(), "new-refresh"));
        when(tokenIssuer.issueAccessToken(refreshToken.getSession())).thenReturn("access-token");

        JwtResponse response = service.refresh(new JwtRefreshTokenDto(APP_ID, "old-refresh"));

        assertTokens(response, "alice", "new-refresh");
        verify(userDetailsService).checkUsable(manager);
    }

    @Test
    @DisplayName("A refused refresh token issues nothing")
    void refresh_withInvalidToken_issuesNothing() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        when(sessionService.requireRefreshableToken(app, "bad")).thenThrow(new InvalidTokenException("Invalid Token"));

        assertThrows(InvalidTokenException.class, () -> service.refresh(new JwtRefreshTokenDto(APP_ID, "bad")));
        verify(sessionService, never()).rotate(any());
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    @DisplayName("A refresh for a user who can no longer log in (e.g. locked) is refused before the token is used up")
    void refresh_forUnusableUser_isRefusedWithoutRotation() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        RefreshToken refreshToken = refreshTokenOf(session(manager, app));
        when(sessionService.requireRefreshableToken(app, "old-refresh")).thenReturn(refreshToken);
        doThrow(new LockedException("locked")).when(userDetailsService).checkUsable(manager);

        assertThrows(LockedException.class, () -> service.refresh(new JwtRefreshTokenDto(APP_ID, "old-refresh")));
        verify(sessionService, never()).rotate(any());
    }

    @Test
    @DisplayName("A client without refresh tokens refuses refreshes")
    void refresh_clientWithoutRefreshTokens_isRefused() {
        app.setRefreshTokenEnabled(false);
        when(clientResolver.resolve(APP_ID)).thenReturn(app);

        assertThrows(UserInputNotValidException.class, () -> service.refresh(new JwtRefreshTokenDto(APP_ID, "refresh-token")));
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("Logout revokes the caller's session")
    void logout_revokesTheCallersSession() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(new AuthenticatedActor(1L, "alice", 100L, false, 42L));

        service.logout();

        verify(sessionService).revoke(42L);
    }

    private void givenAuthenticated(User user) {
        DefaultUserDetails userDetails = new DefaultUserDetails(user);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(userDetails, null, List.of()));
    }

    private void givenSessionStarted(User user, Client client, String refreshToken) {
        UserSession session = session(user, client);
        when(sessionService.start(user, client)).thenReturn(new IssuedSession(session, refreshToken));
        when(tokenIssuer.issueAccessToken(session)).thenReturn("access-token");
    }

    private static void assertTokens(JwtResponse response, String username, String refreshToken) {
        assertEquals(username, response.getUsername());
        assertEquals("access-token", response.getAccessToken());
        assertEquals(refreshToken, response.getRefreshToken());
    }

    private static UserSession session(User user, Client client) {
        UserSession session = new UserSession();
        session.setId(42L);
        session.setUser(user);
        session.setClient(client);
        return session;
    }

    private static RefreshToken refreshTokenOf(UserSession session) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setSession(session);
        return refreshToken;
    }

    private static Client client(String clientId, ClientType type, Tenant tenant) {
        Client client = new Client();
        client.setClientId(clientId);
        client.setType(type);
        client.setTenant(tenant);
        client.setRefreshTokenEnabled(true);
        return client;
    }

    private static User user(String username, UserType type) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setUserType(type);
        return user;
    }
}
