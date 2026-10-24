package com.example.iamsystem.tenant.mapper;

import com.example.iamsystem.tenant.dto.TenantDto;
import com.example.iamsystem.tenant.entity.Tenant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantDto toDto(Tenant tenant);
}
