package com.example.iamsystem.user.mapper;

import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Maps only the profile fields; type, credentials, tenant and account state are set by {@code UserFactory}.
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "username", source = "username")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "email", source = "email")
    User toEntity(UserCreationDto request);

    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "roleIds", source = "roles", qualifiedByName = "roleIds")
    @Mapping(target = "permissionIds", source = "permissions", qualifiedByName = "permissionIds")
    @Mapping(target = "createdBy", source = "createdBy.username")
    UserDto toDto(User user);

    List<UserDto> toDtoList(List<User> users);

    @Named("roleIds")
    default Set<Long> roleIds(Set<Role> roles) {
        return roles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
    }

    @Named("permissionIds")
    default Set<Long> permissionIds(Set<Permission> permissions) {
        return permissions.stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());
    }
}
