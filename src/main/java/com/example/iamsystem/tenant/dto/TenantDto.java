package com.example.iamsystem.tenant.dto;

import com.example.iamsystem.tenant.entity.TenantStatus;

public record TenantDto(Long id, String name, TenantStatus status) {
}
