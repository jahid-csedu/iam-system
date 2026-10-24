package com.example.iamsystem.auth.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import com.example.iamsystem.permission.validation.ValidAction;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuthorizationRequest {
    @NotNull(message = ErrorMessage.SERVICE_NAME_REQUIRED)
    private String serviceName;
    @NotNull(message = ErrorMessage.ACTION_NAME_REQUIRED)
    @ValidAction
    private String action;
}
