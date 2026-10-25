package com.example.iamsystem.role.service;

import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;

/**
 * Builds {@link Role} fixtures for the role service tests. All fixtures belong to the same tenant.
 */
final class RoleTestData {

    static final Tenant TENANT = tenant();

    private RoleTestData() {
    }

    static Role role(long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setTenant(TENANT);
        return role;
    }

    static AuthenticatedActor actor() {
        return new AuthenticatedActor(1L, "actor", TENANT.getId(), false, 10L);
    }

    private static Tenant tenant() {
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        return tenant;
    }
}
