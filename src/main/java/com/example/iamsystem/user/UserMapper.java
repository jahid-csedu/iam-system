package com.example.iamsystem.user;

import com.example.iamsystem.role.Role;
import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "active", constant = "true")
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRegistrationDto userDto);

    @Mapping(target = "roleIds", source = ".", qualifiedByName = "SetRoles")
    UserDto toDto(User user);

    List<UserDto> toDtoList(List<User> users);

    @Named("SetRoles")
    default Set<Long> setRoles(User user) {
        return user.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
    }
}
