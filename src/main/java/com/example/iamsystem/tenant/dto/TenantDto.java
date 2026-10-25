package com.example.iamsystem.tenant.dto;

import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.entity.TenantType;

public record TenantDto(Long id, String name, TenantType type, TenantStatus status) {
}
