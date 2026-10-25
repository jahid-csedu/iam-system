package com.example.iamsystem.client.entity;

/**
 * A {@code SUSPENDED} client refuses logins, refreshes and password resets, and its sessions' tokens stop working.
 */
public enum ClientStatus {
    ACTIVE,
    SUSPENDED
}
