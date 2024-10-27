package com.example.management.role;

import java.util.Collections;
import java.util.Set;

public record RoleDto(
        Long id,
        String name,
        String description,
        Set<Long> permissionIds
) {
    public RoleDto {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be null or empty");
        }

        permissionIds = permissionIds != null ? permissionIds : Collections.emptySet();
    }
}
