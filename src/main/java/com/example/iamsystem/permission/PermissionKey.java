package com.example.iamsystem.permission;

import com.example.iamsystem.permission.entity.PermissionScope;

import static com.example.iamsystem.permission.entity.PermissionScope.PLATFORM;
import static com.example.iamsystem.permission.entity.PermissionScope.TENANT;

/**
 * The permission catalog: {@code <resource>:<action>} keys. Each key exists as a row of the {@code permissions} table,
 * inserted by the migration that adds its feature; a test keeps this enum and the table in sync.
 */
public enum PermissionKey {

    USERS_READ("users:read", TENANT),
    USERS_CREATE("users:create", TENANT),
    USERS_CREATE_MANAGEMENT("users:create-management", TENANT),
    USERS_UPDATE("users:update", TENANT),
    USERS_DELETE("users:delete", TENANT),
    USERS_RESET_PASSWORD("users:reset-password", TENANT),
    ROLES_READ("roles:read", TENANT),
    ROLES_CREATE("roles:create", TENANT),
    ROLES_UPDATE("roles:update", TENANT),
    ROLES_DELETE("roles:delete", TENANT),
    CLIENTS_READ("clients:read", TENANT),
    CLIENTS_CREATE("clients:create", TENANT),
    CLIENTS_UPDATE("clients:update", TENANT),
    CLIENTS_DELETE("clients:delete", TENANT),
    PERMISSIONS_READ("permissions:read", TENANT),
    TENANTS_READ("tenants:read", PLATFORM),
    TENANTS_CREATE("tenants:create", PLATFORM),
    TENANTS_SUSPEND("tenants:suspend", PLATFORM),
    TENANTS_ACTIVATE("tenants:activate", PLATFORM);

    private final String key;
    private final PermissionScope scope;

    PermissionKey(String key, PermissionScope scope) {
        this.key = key;
        this.scope = scope;
    }

    public String key() {
        return key;
    }

    public PermissionScope scope() {
        return scope;
    }
}
