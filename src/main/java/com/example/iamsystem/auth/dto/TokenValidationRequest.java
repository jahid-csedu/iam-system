package com.example.iamsystem.auth.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TokenValidationRequest {
    @NotNull(message = ErrorMessage.TOKEN_REQUIRED)
    private String token;
}
