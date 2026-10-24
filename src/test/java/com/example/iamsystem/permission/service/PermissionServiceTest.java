package com.example.iamsystem.permission.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.permission.dto.PermissionDto;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.mapper.PermissionMapper;
import com.example.iamsystem.permission.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(permissionRepository, Mappers.getMapper(PermissionMapper.class));
    }

    @Test
    @DisplayName("Lists the whole catalog ordered by name")
    void getAllPermissions_returnsTheCatalog() {
        Permission usersList = permission("users:list");
        Permission rolesList = permission("roles:list");
        when(permissionRepository.findAllByOrderByName()).thenReturn(List.of(rolesList, usersList));

        List<PermissionDto> result = service.getAllPermissions();

        assertEquals(List.of("roles:list", "users:list"), result.stream().map(PermissionDto::name).toList());
    }

    @Test
    @DisplayName("A permission is found by id")
    void getPermissionById_returnsPermission() {
        Permission usersList = permission("users:list");
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(usersList));

        assertEquals("users:list", service.getPermissionById(1L).name());
    }

    @Test
    @DisplayName("An unknown permission id is not found")
    void getPermissionById_unknownId_isNotFound() {
        when(permissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> service.getPermissionById(99L));
    }

    private static Permission permission(String name) {
        Permission permission = mock(Permission.class);
        when(permission.getName()).thenReturn(name);
        return permission;
    }
}
