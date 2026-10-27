package com.example.iamsystem.user.dto;

import com.example.iamsystem.tenant.dto.TenantDto;
import com.example.iamsystem.user.entity.UserType;

import java.util.List;

/**
 * The logged-in management user, for the management console.
 *
 * @param homeTenant    the tenant the user belongs to
 * @param workingTenant the tenant the request works in: the home tenant, or the tenant of {@code X-Tenant-Id} for a
 *                      platform user working inside another tenant
 * @param permissions   every permission key the user holds in the working tenant, sorted
 */
public record MeDto(Long id, String username, String fullName, String email, boolean emailVerified, UserType userType,
                    boolean rootUser,
                    TenantDto homeTenant, TenantDto workingTenant, List<String> permissions) {
}
