package com.example.iamsystem.user.model;

import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "active", constant = "true")
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRegistrationDto userDto);

    @Mapping(target = "roleIds", source = ".", qualifiedByName = "SetRoles")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "CreatedBy")
    UserDto toDto(User user);

    List<UserDto> toDtoList(List<User> users);

    @Named("SetRoles")
    default Set<Long> setRoles(User user) {
        return user.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
    }

    @Named("CreatedBy")
    default String createdBy(User user) {
        return Optional.ofNullable(user)
                .map(User::getUsername)
                .orElse(null);
    }
}
