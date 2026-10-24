package com.example.iamsystem.client.entity;

/**
 * User information a client may choose to put into its access tokens, under {@link #claimName()}.
 * Roles and permissions are never offered: the IAM doesn't hand out authorization data to other applications.
 */
public enum TokenClaim {
    USERNAME("username"),
    EMAIL("email"),
    FULL_NAME("full_name"),
    USER_TYPE("user_type"),
    TENANT_ID("tenant_id");

    private final String claimName;

    TokenClaim(String claimName) {
        this.claimName = claimName;
    }

    public String claimName() {
        return claimName;
    }
}
