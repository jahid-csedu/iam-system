package com.example.iamsystem.security.userdetails;

/**
 * The user behind the current request, as the security context holds it, and the login session its access token belongs
 * to. Deliberately not a JPA entity: services load the entities they change, and permissions are resolved by
 * {@code AuthorizationService}.
 *
 * @param tenantId     the tenant this request works in: the boundary of every tenant-owned read and write. It is the
 *                     user's own tenant, unless a platform user works inside another tenant ({@code X-Tenant-Id})
 * @param homeTenantId the tenant the user belongs to
 */
public record AuthenticatedActor(Long userId, String username, Long tenantId, boolean rootUser, Long sessionId,
                                 Long homeTenantId) {

    /**
     * An actor working in their own tenant.
     */
    public AuthenticatedActor(Long userId, String username, Long tenantId, boolean rootUser, Long sessionId) {
        this(userId, username, tenantId, rootUser, sessionId, tenantId);
    }

    public boolean isUser(Long id) {
        return userId.equals(id);
    }

    /**
     * The same actor, working in the given tenant.
     */
    public AuthenticatedActor actingIn(Long tenantId) {
        return new AuthenticatedActor(userId, username, tenantId, rootUser, sessionId, homeTenantId);
    }

    public boolean isActingInOtherTenant() {
        return !tenantId.equals(homeTenantId);
    }
}
