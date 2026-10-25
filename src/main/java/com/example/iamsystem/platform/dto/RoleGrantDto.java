package com.example.iamsystem.platform.dto;

import java.time.Instant;

/**
 * @param tenantId {@code null} for a grant on all standard tenants
 */
public record RoleGrantDto(Long id, Long userId, Long roleId, String roleName, Long tenantId, Instant createdAt) {
}
