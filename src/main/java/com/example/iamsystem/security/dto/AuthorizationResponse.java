package com.example.iamsystem.security.dto;

import lombok.Data;

@Data
public class AuthorizationResponse {
    private boolean authorized;

    public AuthorizationResponse(boolean authorized) {
        this.authorized = authorized;
    }
}
