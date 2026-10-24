package com.example.iamsystem.user.repository;

import com.example.iamsystem.user.entity.User;

import java.util.Optional;

/**
 * Finds a user by username across all tenants, for tests whose usernames are unique in the whole database.
 * Production code always looks users up inside a tenant, because usernames are only unique per tenant.
 */
public final class TestUserLookup {

    private TestUserLookup() {
    }

    public static Optional<User> findByUsername(UserRepository userRepository, String username) {
        return userRepository.findAll().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }
}
