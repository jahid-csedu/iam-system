package com.example.iamsystem.security.dto;

import lombok.Data;

@Data
public class AuthorizationResponse {
    private boolean authorized;
    private String message;
    private static final String ACCESS_DENIED = "Access denied";
    private static final String ACCESS_GRANTED = "Access granted";

    public AuthorizationResponse(boolean authorized) {
        this.authorized = authorized;
        this.message = authorized ? ACCESS_GRANTED : ACCESS_DENIED;
    }
}
