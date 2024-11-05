package com.example.iamsystem.permission;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    @Mapping(target = "action", expression = "java(permission.getAction().name())")
    PermissionDto toDto(Permission permission);

    @Mapping(target = "action", expression = "java(PermissionAction.valueOf(permissionDto.getAction()))")
    Permission toEntity(PermissionDto permissionDto);

    List<PermissionDto> toDto(List<Permission> permissions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "action", expression = "java(PermissionAction.valueOf(permissionDto.getAction()))")
    void toUpdateEntity(@MappingTarget Permission permission, PermissionDto permissionDto);
}
