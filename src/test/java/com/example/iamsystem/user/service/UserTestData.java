package com.example.iamsystem.user.service;

import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;

/**
 * Builds {@link User} fixtures for the user service tests. All fixtures belong to the same tenant.
 */
final class UserTestData {

    static final Tenant TENANT = tenant(100L, "Acme");

    private UserTestData() {
    }

    static Tenant tenant(Long id, String name) {
        Tenant tenant = new Tenant(name, TenantStatus.ACTIVE);
        tenant.setId(id);
        return tenant;
    }

    static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("encoded-" + username);
        user.setTenant(TENANT);
        return user;
    }

    static User rootUser(long id, String username) {
        User user = user(id, username);
        user.setRootUser(true);
        return user;
    }

    static User createdBy(User creator, long id, String username) {
        User user = user(id, username);
        user.setCreatedBy(creator);
        return user;
    }
}
