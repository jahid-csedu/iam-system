package com.example.management.user;

import com.example.management.dto.UserRegistrationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userLocked", constant = "false")
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "passwordExpired", constant = "false")
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRegistrationDto userDto);
}
