package com.example.iamsystem.permission;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.NoAccessException;
import com.example.iamsystem.role.Role;
import com.example.iamsystem.user.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        permission = new Permission();
        permission.setId(1L);
        permission.setAction(PermissionAction.READ);
        permission.setServiceName("TEST_SERVICE");
        permissionDto = new PermissionDto();
        permissionDto.setId(1L);
        permissionDto.setAction("READ");
        permissionDto.setServiceName("TEST_SERVICE");

        user = new User();
        user.setUsername("test");

        role = new Role();
        role.setPermissions(Set.of(permission));

        user.setRoles(Set.of(role));
    }

    @Test
    void savePermission_savesAndReturnsPermission() {
        when(permissionRepository.findAllByServiceName("TEST_SERVICE")).thenReturn(Collections.emptyList());
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission);

        PermissionDto result = permissionService.savePermission(permissionDto);

        assertNotNull(result);
        assertEquals(permissionDto.getServiceName(), result.getServiceName());
        assertEquals(permissionDto.getAction(), result.getAction());
        verify(permissionRepository, times(1)).save(any(Permission.class));
    }

    @Test
    void savePermission_throwsExceptionWhenPermissionExists() {
        when(permissionRepository.findAllByServiceName("TEST_SERVICE")).thenReturn(Collections.singletonList(permission));

        assertThrows(IllegalArgumentException.class, () -> permissionService.savePermission(permissionDto));
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
        when(permissionRepository.findAllByServiceName("TEST_SERVICE")).thenReturn(List.of(permission));

        List<PermissionDto> result = permissionService.getPermissionByServiceName("TEST_SERVICE");

        assertNotNull(result);
        assertEquals(permissionDto.getServiceName(), result.getFirst().getServiceName());
        assertEquals(permissionDto.getAction(), result.getFirst().getAction());
    }

    @Test
    void getAllPermissions_returnsListOfPermissions() {
        when(permissionRepository.findAll()).thenReturn(Collections.singletonList(permission));

        List<PermissionDto> result = permissionService.getAllPermissions();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testHasPermission_whenUserHasProvidedPermission_thenReturnTrue() {
        assertTrue(permissionService.hasPermission(user, "TEST_SERVICE:READ"));
    }

    @Test
    void testHasPermission_whenUserDoesNotHaveProvidedPermission_thenReturnFalse() {
        assertFalse(permissionService.hasPermission(user, "TEST_SERVICE:WRITE"));
    }
}