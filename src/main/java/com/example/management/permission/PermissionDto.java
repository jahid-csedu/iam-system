package com.example.management.permission;

import com.example.management.constant.ErrorMessage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PermissionDto {
    private Long id;
    @NotNull(message = ErrorMessage.SERVICE_NAME_REQUIRED)
    private String serviceName;
    @NotNull(message = ErrorMessage.ACTION_NAME_REQUIRED)
    private String action;
    private String description;
}
