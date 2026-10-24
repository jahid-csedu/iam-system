package com.example.iamsystem.tenant.dto;

import com.example.iamsystem.client.dto.ClientDto;
import com.example.iamsystem.user.dto.UserDto;

/**
 * The new tenant, its root user, and the management console client the root user logs in through.
 */
public record TenantSignupResultDto(TenantDto tenant, UserDto rootUser, ClientDto managementConsole) {
}
