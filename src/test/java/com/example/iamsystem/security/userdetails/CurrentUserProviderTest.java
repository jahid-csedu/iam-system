package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.NoAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserProviderTest {

    private final CurrentUserProvider currentUserProvider = new CurrentUserProvider();
    private final AuthenticatedActor actor = new AuthenticatedActor(1L, "alice", 7L, false, 10L);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Without authentication there is no current actor")
    void currentActor_whenNoAuthentication_isEmpty() {
        assertTrue(currentUserProvider.currentActor().isEmpty());
    }

    @Test
    @DisplayName("An anonymous caller is not a current actor")
    void currentActor_whenAnonymous_isEmpty() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertTrue(currentUserProvider.currentActor().isEmpty());
    }

    @Test
    @DisplayName("An authenticated caller is the current actor")
    void currentActor_whenAuthenticated_returnsActor() {
        authenticate();

        assertSame(actor, currentUserProvider.currentActor().orElseThrow());
        assertSame(actor, currentUserProvider.requireCurrentActor());
    }

    @Test
    @DisplayName("Requiring a current actor without authentication throws")
    void requireCurrentActor_whenNotAuthenticated_throws() {
        NoAccessException exception = assertThrows(NoAccessException.class, currentUserProvider::requireCurrentActor);
        assertEquals("No logged in user found", exception.getMessage());
    }

    @Test
    @DisplayName("The current tenant is the current actor's tenant")
    void requireCurrentTenantId_returnsTheCurrentActorsTenant() {
        authenticate();

        assertEquals(7L, currentUserProvider.requireCurrentTenantId());
    }

    @Test
    @DisplayName("Requiring the current tenant without authentication throws")
    void requireCurrentTenantId_whenNotAuthenticated_throws() {
        assertThrows(NoAccessException.class, currentUserProvider::requireCurrentTenantId);
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
    }
}
