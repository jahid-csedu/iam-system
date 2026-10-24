package com.example.iamsystem.user.entity;

/**
 * Set when a user is created and never changed; to change it, create a new user.
 * <ul>
 *     <li>MANAGEMENT: uses the management API; may hold roles and direct permissions. Root users are management users.</li>
 *     <li>REGULAR: logs in, refreshes tokens and manages their own password; never holds roles or permissions.</li>
 * </ul>
 */
public enum UserType {
    MANAGEMENT,
    REGULAR
}
