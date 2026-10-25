package com.example.iamsystem.auth.service;

import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.auth.dto.TokenValidationRequest;
import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.ClientType;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.InvalidClientException;
import com.example.iamsystem.common.exception.JwtException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.jwt.TokenIssuer;
import com.example.iamsystem.security.jwt.TokenVerifier;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.security.userdetails.DefaultUserDetailsService;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private TokenIssuer tokenIssuer;
    @Mock
    private TokenVerifier tokenVerifier;

    @InjectMocks
    private AuthenticationService service;

    private final Claims claims = Jwts.claims().subject("1").add("client_id", APP_ID).build();
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
    @DisplayName("Login authenticates the username inside the client's tenant and issues tokens for that client")
    void login_authenticatesInsideTheClientsTenant() {
        when(clientResolver.resolve(CONSOLE_ID)).thenReturn(console);
        givenAuthenticated(manager);
        givenIssuedTokens(manager, CONSOLE_ID);

        JwtResponse response = service.login(new UserLoginDto(CONSOLE_ID, "alice", "Password1!"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> request = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(request.capture());
        assertEquals("alice", request.getValue().getName());
        assertEquals(100L, request.getValue().getDetails());
        assertTokens(response, "alice");
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("A regular user can log in through an application client")
    void login_regularUserThroughApplication_isAllowed() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        givenAuthenticated(regular);
        givenIssuedTokens(regular, APP_ID);

        assertTokens(service.login(new UserLoginDto(APP_ID, "carol", "Password1!")), "carol");
    }

    @Test
    @DisplayName("The management console refuses regular users")
    void login_regularUserThroughConsole_isDenied() {
        when(clientResolver.resolve(CONSOLE_ID)).thenReturn(console);
        givenAuthenticated(regular);

        assertThrows(NoAccessException.class, () -> service.login(new UserLoginDto(CONSOLE_ID, "carol", "Password1!")));
        verify(tokenIssuer, never()).issueAccessToken(any(), any());
    }

    @Test
    @DisplayName("An unknown client_id is rejected before any authentication")
    void login_unknownClient_isRejected() {
        when(clientResolver.resolve("nope")).thenThrow(new InvalidClientException("Unknown client_id"));

        assertThrows(InvalidClientException.class, () -> service.login(new UserLoginDto("nope", "alice", "Password1!")));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("Login with bad credentials issues no tokens")
    void login_withBadCredentials_issuesNoTokens() {
        when(clientResolver.resolve(CONSOLE_ID)).thenReturn(console);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> service.login(new UserLoginDto(CONSOLE_ID, "alice", "wrong")));
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    @DisplayName("A valid refresh token issued through the same client gets new tokens")
    void refresh_withValidRefreshToken_issuesNewTokens() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        givenRefreshClaims();
        when(userDetailsService.requireUsableUser(1L)).thenReturn(new DefaultUserDetails(manager));
        when(tokenVerifier.isIssuedFor(claims, manager)).thenReturn(true);
        givenIssuedTokens(manager, APP_ID);

        assertTokens(service.refresh(new JwtRefreshTokenDto(APP_ID, "refresh-token")), "alice");
    }

    @Test
    @DisplayName("A refresh token issued through another client is rejected")
    void refresh_throughAnotherClient_isRejected() {
        when(clientResolver.resolve(CONSOLE_ID)).thenReturn(console);
        givenRefreshClaims();

        assertThrows(JwtException.class, () -> service.refresh(new JwtRefreshTokenDto(CONSOLE_ID, "refresh-token")));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("A refresh token issued for an older user version is rejected")
    void refresh_withRevokedRefreshToken_isRejected() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        givenRefreshClaims();
        when(userDetailsService.requireUsableUser(1L)).thenReturn(new DefaultUserDetails(manager));
        when(tokenVerifier.isIssuedFor(claims, manager)).thenReturn(false);

        assertThrows(JwtException.class, () -> service.refresh(new JwtRefreshTokenDto(APP_ID, "refresh-token")));
        verify(tokenIssuer, never()).issueAccessToken(any(), any());
    }

    @Test
    @DisplayName("A client without refresh tokens issues only an access token at login")
    void login_clientWithoutRefreshTokens_issuesNoRefreshToken() {
        app.setRefreshTokenEnabled(false);
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        givenAuthenticated(regular);
        when(tokenIssuer.issueAccessToken(regular, app)).thenReturn("access-token");

        JwtResponse response = service.login(new UserLoginDto(APP_ID, "carol", "Password1!"));

        assertEquals("access-token", response.getAccessToken());
        assertNull(response.getRefreshToken());
        verify(tokenIssuer, never()).issueRefreshToken(any(), any());
    }

    @Test
    @DisplayName("A client without refresh tokens refuses refreshes")
    void refresh_clientWithoutRefreshTokens_isRefused() {
        app.setRefreshTokenEnabled(false);
        when(clientResolver.resolve(APP_ID)).thenReturn(app);

        assertThrows(UserInputNotValidException.class, () -> service.refresh(new JwtRefreshTokenDto(APP_ID, "refresh-token")));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("An expired refresh token is rejected as an invalid token")
    void refresh_withExpiredRefreshToken_isRejectedAsInvalidToken() {
        when(clientResolver.resolve(APP_ID)).thenReturn(app);
        when(tokenVerifier.parseClaims("expired", REFRESH_TOKEN)).thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThrows(JwtException.class, () -> service.refresh(new JwtRefreshTokenDto(APP_ID, "expired")));
    }

    @Test
    @DisplayName("Validation reports whether the access token is valid")
    void validate_reportsWhetherTheAccessTokenIsValid() {
        TokenValidationRequest request = new TokenValidationRequest();
        request.setToken("access-token");
        when(tokenVerifier.isValid("access-token", ACCESS_TOKEN)).thenReturn(true);

        assertTrue(service.validate(request).isStatus());
    }

    private void givenAuthenticated(User user) {
        DefaultUserDetails userDetails = new DefaultUserDetails(user);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(userDetails, null, List.of()));
    }

    private void givenRefreshClaims() {
        when(tokenVerifier.parseClaims("refresh-token", REFRESH_TOKEN)).thenReturn(claims);
        when(tokenVerifier.userIdOf(claims)).thenReturn(1L);
        when(tokenVerifier.clientIdOf(claims)).thenReturn(APP_ID);
    }

    private void givenIssuedTokens(User user, String clientId) {
        Client client = clientId.equals(CONSOLE_ID) ? console : app;
        when(tokenIssuer.issueAccessToken(user, client)).thenReturn("access-token");
        when(tokenIssuer.issueRefreshToken(user, client)).thenReturn("refresh-token");
    }

    private static void assertTokens(JwtResponse response, String username) {
        assertEquals(username, response.getUsername());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
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
