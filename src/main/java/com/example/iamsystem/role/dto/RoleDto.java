package com.example.iamsystem.role.dto;

import java.util.Set;

public record RoleDto(Long id, String name, String description, Set<Long> permissionIds) {
}
