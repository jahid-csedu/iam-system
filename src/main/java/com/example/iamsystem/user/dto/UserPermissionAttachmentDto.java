package com.example.iamsystem.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

import static com.example.iamsystem.common.exception.ErrorMessage.PERMISSION_LIST_NOT_EMPTY;
import static com.example.iamsystem.common.exception.ErrorMessage.USERNAME_REQUIRED;

/**
 * Permissions to grant to, or take from, a management user directly (next to the ones from their roles).
 */
public record UserPermissionAttachmentDto(@NotBlank(message = USERNAME_REQUIRED) String username,
                                          @NotEmpty(message = PERMISSION_LIST_NOT_EMPTY) Set<Long> permissionIds) {
}
