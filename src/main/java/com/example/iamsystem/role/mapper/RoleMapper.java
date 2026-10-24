package com.example.iamsystem.role.mapper;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.role.dto.RoleDto;
import com.example.iamsystem.role.dto.RoleRequestDto;
import com.example.iamsystem.role.entity.Role;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "permissionIds", source = "permissions", qualifiedByName = "permissionIds")
    RoleDto toDto(Role role);

    List<RoleDto> toDtoList(List<Role> roles);

    /**
     * Maps only the editable fields; the tenant is set by {@code RoleService}.
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    Role toEntity(RoleRequestDto request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    void updateEntity(@MappingTarget Role role, RoleRequestDto request);

    @Named("permissionIds")
    default Set<Long> permissionIds(Set<Permission> permissions) {
        return permissions.stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());
    }
}
