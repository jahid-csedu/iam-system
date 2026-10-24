package com.example.iamsystem.client.entity;

/**
 * MANAGEMENT_CONSOLE: the one built-in client of a tenant; only management users log in through it, and it can't be deleted.
 * APPLICATION: a client the tenant registers; all users of the tenant may log in through it.
 */
public enum ClientType {
    MANAGEMENT_CONSOLE,
    APPLICATION
}
