package com.example.management.security.dto;

import com.example.management.constant.ErrorMessage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckAccessDto {
    @NotNull(message = ErrorMessage.USERNAME_REQUIRED)
    private String username;
    @NotNull(message = ErrorMessage.SERVICE_NAME_REQUIRED)
    private String serviceName;
    @NotNull(message = ErrorMessage.ACTION_NAME_REQUIRED)
    private String action;
}
