package com.example.iamsystem.authorization;

import java.util.Set;

/**
 * The tenants a platform user holds a PLATFORM permission on: all of them, or the listed ones (possibly none).
 */
public record TenantScope(boolean allTenants, Set<Long> tenantIds) {

    public static final TenantScope ALL = new TenantScope(true, Set.of());

    public static TenantScope of(Set<Long> tenantIds) {
        return new TenantScope(false, Set.copyOf(tenantIds));
    }

    public boolean covers(Long tenantId) {
        return allTenants || tenantIds.contains(tenantId);
    }
}
