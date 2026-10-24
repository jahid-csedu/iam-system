package com.example.iamsystem.permission.entity;

/**
 * Who a permission is for. TENANT permissions act inside one tenant and may be put into that tenant's roles;
 * PLATFORM permissions act across tenants and are held only by platform super users.
 */
public enum PermissionScope {
    TENANT,
    PLATFORM
}
