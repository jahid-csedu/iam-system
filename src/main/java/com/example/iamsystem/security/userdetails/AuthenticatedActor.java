package com.example.iamsystem.security.userdetails;

/**
 * The user behind the current request, as the security context holds it, and the login session its access token belongs
 * to. Deliberately not a JPA entity: services load the entities they change, and permissions are resolved by
 * {@code AuthorizationService}.
 */
public record AuthenticatedActor(Long userId, String username, Long tenantId, boolean rootUser, Long sessionId) {

    public boolean isUser(Long id) {
        return userId.equals(id);
    }
}
