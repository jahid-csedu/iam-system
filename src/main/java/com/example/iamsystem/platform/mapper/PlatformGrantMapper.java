package com.example.iamsystem.platform.mapper;

import com.example.iamsystem.platform.dto.PermissionGrantDto;
import com.example.iamsystem.platform.dto.RoleGrantDto;
import com.example.iamsystem.platform.entity.PlatformPermissionGrant;
import com.example.iamsystem.platform.entity.PlatformRoleGrant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlatformGrantMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "tenantId", source = "tenant.id")
    RoleGrantDto toDto(PlatformRoleGrant grant);

    List<RoleGrantDto> toRoleGrantDtos(List<PlatformRoleGrant> grants);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "permissionId", source = "permission.id")
    @Mapping(target = "permissionName", source = "permission.name")
    @Mapping(target = "tenantId", source = "tenant.id")
    PermissionGrantDto toDto(PlatformPermissionGrant grant);

    List<PermissionGrantDto> toPermissionGrantDtos(List<PlatformPermissionGrant> grants);
}
