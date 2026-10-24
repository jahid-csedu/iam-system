package com.example.iamsystem.auth.service;

import com.example.iamsystem.auth.dto.AuthorizationRequest;
import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.auth.dto.TokenValidationRequest;
import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.JwtException;
import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.security.userdetails.DefaultUserDetailsService;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static com.example.iamsystem.permission.entity.PermissionAction.READ;
import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private DefaultUserDetailsService userDetailsService;
    @Mock
    private JwtTokenUtil jwtTokenUtil;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AuthenticationService service;

    private User user;
    private DefaultUserDetails userDetails;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("alice");
        userDetails = new DefaultUserDetails(user);
    }

    @Test
    @DisplayName("Login issues tokens for the authenticated principal without reloading the user")
    void login_issuesTokensForTheAuthenticatedPrincipalWithoutReloadingTheUser() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        givenIssuedTokens();

        JwtResponse response = service.login(new UserLoginDto("alice", "Password1!"));

        assertTokens(response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Login with bad credentials issues no tokens")
    void login_withBadCredentials_issuesNoTokens() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> service.login(new UserLoginDto("alice", "wrong")));
        verifyNoInteractions(jwtTokenUtil);
    }

    @Test
    @DisplayName("A valid refresh token gets new tokens")
    void refresh_withValidRefreshToken_issuesNewTokens() {
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtTokenUtil.validateToken("refresh-token", userDetails, REFRESH_TOKEN)).thenReturn(true);
        givenIssuedTokens();

        assertTokens(service.refresh(new JwtRefreshTokenDto("alice", "refresh-token")));
    }

    @Test
    @DisplayName("An invalid refresh token is rejected")
    void refresh_withInvalidRefreshToken_isRejected() {
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtTokenUtil.validateToken("refresh-token", userDetails, REFRESH_TOKEN)).thenReturn(false);

        assertThrows(JwtException.class, () -> service.refresh(new JwtRefreshTokenDto("alice", "refresh-token")));
        verify(jwtTokenUtil, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("An expired refresh token is rejected as an invalid token")
    void refresh_withExpiredRefreshToken_isRejectedAsInvalidToken() {
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtTokenUtil.validateToken("expired", userDetails, REFRESH_TOKEN))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThrows(JwtException.class, () -> service.refresh(new JwtRefreshTokenDto("alice", "expired")));
    }

    @Test
    @DisplayName("Validation reports whether the access token is valid")
    void validate_reportsWhetherTheAccessTokenIsValid() {
        TokenValidationRequest request = new TokenValidationRequest();
        request.setToken("access-token");
        when(jwtTokenUtil.validateToken("access-token", ACCESS_TOKEN)).thenReturn(true);

        assertTrue(service.validate(request).isStatus());
    }

    @Test
    @DisplayName("Authorize asks the authorization service about the current user")
    void authorize_asksAuthorizationServiceForTheCurrentUser() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setServiceName("BILLING");
        request.setAction("READ");
        when(currentUserProvider.requireCurrentUser()).thenReturn(user);
        when(authorizationService.hasPermission(user, "BILLING", READ)).thenReturn(true);

        assertTrue(service.authorize(request).isAuthorized());
    }

    private void givenIssuedTokens() {
        when(jwtTokenUtil.generateToken(userDetails, ACCESS_TOKEN)).thenReturn("access-token");
        when(jwtTokenUtil.generateToken(userDetails, REFRESH_TOKEN)).thenReturn("refresh-token");
    }

    private static void assertTokens(JwtResponse response) {
        assertEquals("alice", response.getUsername());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
    }
}
