package com.example.iamsystem.security.dto;

import lombok.Data;

@Data
public class TokenValidationResponse {
    private boolean status;

    public TokenValidationResponse(boolean isValid) {
        this.status = isValid;
    }
}
