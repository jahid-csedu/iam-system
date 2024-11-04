package com.example.iamsystem.role;

import com.example.iamsystem.permission.Permission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    @Mapping(target = "permissionIds", source = ".", qualifiedByName = "SetPermissions")
    RoleDto toDto(Role role);

    @Mapping(target = "permissions", ignore = true)
    Role toEntity(RoleDto roleDto);

    List<RoleDto> toDto(List<Role> roles);

    @Mapping(target = "id", ignore = true)
    void toUpdateEntity(@MappingTarget Role role, RoleDto roleDto);

    @Named("SetPermissions")
    default Set<Long> setPermissions(Role role) {
        return role.getPermissions().stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());
    }
}
