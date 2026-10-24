package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.user.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Builds the principal that {@code JwtRequestFilter} puts into the security context, for tests.
 */
public final class TestActors {

    private TestActors() {
    }

    public static AuthenticatedActor actorOf(User user) {
        return new AuthenticatedActor(user.getId(), user.getUsername(), user.getTenant().getId(), user.isRootUser());
    }

    /**
     * Authenticates a MockMvc request as the given (persisted) user.
     */
    public static RequestPostProcessor actingAs(User user) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(actorOf(user), null, List.of()));
    }
}
