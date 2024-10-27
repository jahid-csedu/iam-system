package com.example.management.permission;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    PermissionDto toDto(Permission permission);
    Permission toEntity(PermissionDto permissionDto);

    List<PermissionDto> toDto(List<Permission> permissions);

    @Mapping(target = "id", ignore = true)
    void toUpdateEntity(@MappingTarget Permission permission, PermissionDto permissionDto);
}
