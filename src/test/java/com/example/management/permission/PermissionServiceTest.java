package com.example.management.permission;

import com.example.management.exception.DataNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    private Permission permission;
    private PermissionDto permissionDto;

    @BeforeEach
    void setUp() {
        permission = new Permission();
        permission.setId(1L);
        permission.setAction("READ");
        permission.setServiceName("TEST_SERVICE");
        permissionDto = new PermissionDto();
        permissionDto.setId(1L);
        permissionDto.setAction("READ");
        permissionDto.setServiceName("TEST_SERVICE");
    }

    @Test
    void savePermission_savesAndReturnsPermission() {
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission);

        PermissionDto result = permissionService.savePermission(permissionDto);

        assertNotNull(result);
        assertEquals(permissionDto.getServiceName(), result.getServiceName());
        assertEquals(permissionDto.getAction(), result.getAction());
        verify(permissionRepository, times(1)).save(any(Permission.class));
    }

    @Test
    void getPermissionById_returnsPermission() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(permission));

        PermissionDto result = permissionService.getPermissionById(1L);

        assertNotNull(result);
        assertEquals(permissionDto.getServiceName(), result.getServiceName());
        assertEquals(permissionDto.getAction(), result.getAction());
    }

    @Test
    void getPermissionById_throwsExceptionWhenNotFound() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> permissionService.getPermissionById(1L));
    }

    @Test
    void updatePermission_updatesAndReturnsPermission() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(permission));
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission);

        PermissionDto result = permissionService.updatePermission(1L, permissionDto);

        assertNotNull(result);
        assertEquals(permissionDto.getServiceName(), result.getServiceName());
        assertEquals(permissionDto.getAction(), result.getAction());
        verify(permissionRepository, times(1)).save(any(Permission.class));
    }

    @Test
    void updatePermission_throwsExceptionWhenNotFound() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> permissionService.updatePermission(1L, permissionDto));
    }

    @Test
    void deletePermissionById_deletesPermission() {
        doNothing().when(permissionRepository).deleteById(1L);

        permissionService.deletePermissionById(1L);

        verify(permissionRepository, times(1)).deleteById(1L);
    }

    @Test
    void getPermissionByName_returnsPermissionService() {
        when(permissionRepository.findByServiceName("TEST_SERVICE")).thenReturn(Optional.of(permission));

        PermissionDto result = permissionService.getPermissionByServiceName("TEST_SERVICE");

        assertNotNull(result);
        assertEquals(permissionDto.getServiceName(), result.getServiceName());
        assertEquals(permissionDto.getAction(), result.getAction());
    }

    @Test
    void getPermissionByServiceName_throwsExceptionWhenNotFound() {
        when(permissionRepository.findByServiceName("TEST_SERVICE")).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> permissionService.getPermissionByServiceName("TEST_SERVICE"));
    }

    @Test
    void getAllPermissions_returnsListOfPermissions() {
        when(permissionRepository.findAll()).thenReturn(Collections.singletonList(permission));

        List<PermissionDto> result = permissionService.getAllPermissions();

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}