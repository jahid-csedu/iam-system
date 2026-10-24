package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthenticationProviderTest {

    private static final Long TENANT_ID = 100L;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private DefaultUserDetailsService userDetailsService;

    private TenantAuthenticationProvider provider;
    private User user;

    @BeforeEach
    void setUp() {
        provider = new TenantAuthenticationProvider(userDetailsService, passwordEncoder);
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setActive(true);
    }

    @Test
    @DisplayName("The right password for a user of the request's tenant authenticates")
    void authenticate_rightPasswordInTenant_succeeds() {
        when(userDetailsService.findInTenant("alice", TENANT_ID)).thenReturn(Optional.of(new DefaultUserDetails(user)));

        Authentication result = provider.authenticate(loginRequest("Password1!", TENANT_ID));

        assertSame(user, ((DefaultUserDetails) result.getPrincipal()).user());
    }

    @Test
    @DisplayName("A wrong password is bad credentials")
    void authenticate_wrongPassword_isBadCredentials() {
        when(userDetailsService.findInTenant("alice", TENANT_ID)).thenReturn(Optional.of(new DefaultUserDetails(user)));

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(loginRequest("Wrong123!", TENANT_ID)));
    }

    @Test
    @DisplayName("A username unknown in the tenant looks exactly like a wrong password")
    void authenticate_unknownUser_isBadCredentials() {
        when(userDetailsService.findInTenant("alice", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(loginRequest("Password1!", TENANT_ID)));
    }

    @Test
    @DisplayName("A locked account is refused even with the right password")
    void authenticate_lockedUser_isLocked() {
        user.setUserLocked(true);
        user.setAccountLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
        when(userDetailsService.findInTenant("alice", TENANT_ID)).thenReturn(Optional.of(new DefaultUserDetails(user)));

        assertThrows(LockedException.class, () -> provider.authenticate(loginRequest("Password1!", TENANT_ID)));
    }

    @Test
    @DisplayName("A login request without a tenant is refused")
    void authenticate_withoutTenant_isRefused() {
        assertThrows(InternalAuthenticationServiceException.class, () -> provider.authenticate(loginRequest("Password1!", null)));
    }

    private static UsernamePasswordAuthenticationToken loginRequest(String password, Long tenantId) {
        UsernamePasswordAuthenticationToken request = UsernamePasswordAuthenticationToken.unauthenticated("alice", password);
        request.setDetails(tenantId);
        return request;
    }
}
