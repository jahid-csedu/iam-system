package com.example.iamsystem.role.dto;

import jakarta.validation.constraints.NotBlank;

import static com.example.iamsystem.common.exception.ErrorMessage.ROLE_NAME_REQUIRED;

/**
 * Body for creating or updating a role. Permissions are attached separately through {@code /api/roles/permissions}.
 */
public record RoleRequestDto(@NotBlank(message = ROLE_NAME_REQUIRED) String name, String description) {
}
