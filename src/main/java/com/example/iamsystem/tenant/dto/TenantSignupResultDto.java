package com.example.iamsystem.tenant.dto;

import com.example.iamsystem.user.dto.UserDto;

public record TenantSignupResultDto(TenantDto tenant, UserDto rootUser) {
}
