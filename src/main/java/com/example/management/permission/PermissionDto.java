package com.example.management.permission;

public record PermissionDto(Long id, String name, String description) {
    public PermissionDto {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Permission name cannot be null or empty");
        }
    }
}
