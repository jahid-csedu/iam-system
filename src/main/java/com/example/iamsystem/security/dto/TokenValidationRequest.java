package com.example.iamsystem.security.dto;

import com.example.iamsystem.constant.ErrorMessage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TokenValidationRequest {
    @NotNull(message = ErrorMessage.TOKEN_REQUIRED)
    private String token;
}
