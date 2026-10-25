package com.example.iamsystem.platform.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.ROLE_LIST_NOT_EMPTY;
import static com.example.iamsystem.common.exception.ErrorMessage.USER_ID_REQUIRED;

/**
 * Grants platform roles to a platform user on one tenant, or on all standard tenants when {@code tenantId} is empty.
 */
public record RoleGrantRequestDto(@NotNull(message = USER_ID_REQUIRED) Long userId,
                                  @NotEmpty(message = ROLE_LIST_NOT_EMPTY) Set<Long> roleIds,
                                  Long tenantId) {
}
