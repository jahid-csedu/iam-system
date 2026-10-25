package com.example.iamsystem.platform.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSION_LIST_NOT_EMPTY;
import static com.example.iamsystem.common.exception.ErrorMessage.USER_ID_REQUIRED;

/**
 * Grants permissions to a platform user on one tenant, or on all standard tenants when {@code tenantId} is empty.
 */
public record PermissionGrantRequestDto(@NotNull(message = USER_ID_REQUIRED) Long userId,
                                        @NotEmpty(message = PERMISSION_LIST_NOT_EMPTY) Set<Long> permissionIds,
                                        Long tenantId) {
}
