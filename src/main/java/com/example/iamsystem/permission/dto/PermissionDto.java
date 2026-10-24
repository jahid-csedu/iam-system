package com.example.iamsystem.permission.dto;

import com.example.iamsystem.permission.entity.PermissionScope;

public record PermissionDto(Long id, String name, PermissionScope scope, String description) {
}
