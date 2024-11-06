package com.example.iamsystem.security.dto;

import lombok.Data;

@Data
public class TokenValidationResponse {
    private boolean status;
    private String message;
    private static final String VALID = "Valid";
    private static final String INVALID = "Invalid";

    public TokenValidationResponse(boolean isValid) {
        this.status = isValid;
        this.message = isValid ? VALID : INVALID;
    }
}
