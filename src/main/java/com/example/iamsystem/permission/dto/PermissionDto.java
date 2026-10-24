package com.example.iamsystem.permission.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import com.example.iamsystem.permission.validation.ValidAction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionDto {
    private Long id;
    @NotNull(message = ErrorMessage.SERVICE_NAME_REQUIRED)
    private String serviceName;
    @NotNull(message = ErrorMessage.ACTION_NAME_REQUIRED)
    @ValidAction
    private String action;
    private String description;
}
