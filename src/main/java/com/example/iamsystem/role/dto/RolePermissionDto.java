package com.example.iamsystem.role.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSION_LIST_NOT_EMPTY;
import static com.example.iamsystem.common.exception.ErrorMessage.ROLE_ID_REQUIRED;

public record RolePermissionDto(@NotNull(message = ROLE_ID_REQUIRED) Long roleId,
                                @NotEmpty(message = PERMISSION_LIST_NOT_EMPTY) Set<Long> permissionIds) {
}
