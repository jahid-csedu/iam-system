package com.example.iamsystem.user.service;

import com.example.iamsystem.user.entity.User;

/**
 * Builds {@link User} fixtures for the user service tests.
 */
final class UserTestData {

    private UserTestData() {
    }

    static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("encoded-" + username);
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
