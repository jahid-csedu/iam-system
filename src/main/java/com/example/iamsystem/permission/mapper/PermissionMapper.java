package com.example.iamsystem.permission.mapper;

import com.example.iamsystem.permission.dto.PermissionDto;
import com.example.iamsystem.permission.entity.Permission;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    PermissionDto toDto(Permission permission);

    List<PermissionDto> toDtoList(List<Permission> permissions);
}
