package com.example.iamsystem.platform.dto;

import java.time.Instant;

/**
 * @param tenantId {@code null} for a grant on all standard tenants
 */
public record PermissionGrantDto(Long id, Long userId, Long permissionId, String permissionName, Long tenantId,
                                 Instant createdAt) {
}
