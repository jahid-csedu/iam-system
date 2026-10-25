package com.example.iamsystem.platform.dto;

import java.util.List;

/**
 * Everything a platform user is granted on other tenants.
 */
public record PlatformGrantsDto(List<RoleGrantDto> roleGrants) {
}
