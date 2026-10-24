package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.NoAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.USER_NOT_LOGGED_IN;

/**
 * Single source of truth for the user making the current request.
 */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedActor> currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            return Optional.of(actor);
        }
        return Optional.empty();
    }

    public AuthenticatedActor requireCurrentActor() {
        return currentActor().orElseThrow(() -> new NoAccessException(USER_NOT_LOGGED_IN));
    }

    /**
     * The tenant of the current user: the boundary for every tenant-owned read and write.
     */
    public Long requireCurrentTenantId() {
        return requireCurrentActor().tenantId();
    }
}
