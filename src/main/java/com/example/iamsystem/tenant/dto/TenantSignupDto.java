package com.example.iamsystem.tenant.dto;

import com.example.iamsystem.common.exception.ErrorMessage;
import com.example.iamsystem.user.dto.UserCreationDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Creates a tenant together with its root user.
 */
public record TenantSignupDto(
        @NotBlank(message = ErrorMessage.TENANT_NAME_REQUIRED)
        String tenantName,

        @NotNull(message = ErrorMessage.ROOT_USER_REQUIRED)
        @Valid
        UserCreationDto rootUser
) {
}
