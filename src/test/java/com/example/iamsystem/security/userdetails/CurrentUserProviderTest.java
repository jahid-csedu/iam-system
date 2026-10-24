package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserProviderTest {

    private final CurrentUserProvider currentUserProvider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Without authentication there is no current user")
    void currentUser_whenNoAuthentication_isEmpty() {
        assertTrue(currentUserProvider.currentUser().isEmpty());
    }

    @Test
    @DisplayName("An anonymous caller is not a current user")
    void currentUser_whenAnonymous_isEmpty() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertTrue(currentUserProvider.currentUser().isEmpty());
    }

    @Test
    @DisplayName("An authenticated caller is the current user")
    void currentUser_whenAuthenticated_returnsUser() {
        User user = new User();
        user.setUsername("alice");
        authenticate(user);

        assertSame(user, currentUserProvider.currentUser().orElseThrow());
        assertSame(user, currentUserProvider.requireCurrentUser());
    }

    @Test
    @DisplayName("Requiring a current user without authentication throws")
    void requireCurrentUser_whenNotAuthenticated_throws() {
        NoAccessException exception = assertThrows(NoAccessException.class, currentUserProvider::requireCurrentUser);
        assertEquals("No logged in user found", exception.getMessage());
    }

    @Test
    @DisplayName("The current tenant is the current user's tenant")
    void requireCurrentTenantId_returnsTheCurrentUsersTenant() {
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(7L);
        User user = new User();
        user.setTenant(tenant);
        authenticate(user);

        assertEquals(7L, currentUserProvider.requireCurrentTenantId());
    }

    @Test
    @DisplayName("Requiring the current tenant without authentication throws")
    void requireCurrentTenantId_whenNotAuthenticated_throws() {
        assertThrows(NoAccessException.class, currentUserProvider::requireCurrentTenantId);
    }

    private void authenticate(User user) {
        DefaultUserDetails userDetails = new DefaultUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
