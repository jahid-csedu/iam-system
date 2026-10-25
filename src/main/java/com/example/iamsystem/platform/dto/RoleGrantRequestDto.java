package com.example.iamsystem.platform.dto;

import jakarta.validation.constraints.NotNull;

import static com.example.iamsystem.common.exception.ErrorMessage.ROLE_ID_REQUIRED;
import static com.example.iamsystem.common.exception.ErrorMessage.USER_ID_REQUIRED;

/**
 * Grants a platform role to a platform user on one tenant, or on all standard tenants when {@code tenantId} is empty.
 */
public record RoleGrantRequestDto(@NotNull(message = USER_ID_REQUIRED) Long userId,
                                  @NotNull(message = ROLE_ID_REQUIRED) Long roleId,
                                  Long tenantId) {
}
