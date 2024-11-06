package com.example.iamsystem.security.dto;

import com.example.iamsystem.constant.ErrorMessage;
import com.example.iamsystem.util.validators.action.ValidAction;
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
