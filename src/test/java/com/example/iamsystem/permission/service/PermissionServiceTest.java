package com.example.iamsystem.permission.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.PermissionAlreadyExistsException;
import com.example.iamsystem.permission.dto.PermissionDto;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
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

import static com.example.iamsystem.permission.entity.PermissionAction.READ;
import static com.example.iamsystem.permission.entity.PermissionAction.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    private PermissionService service;
    private Permission readPermission;

    @BeforeEach
    void setUp() {
        PermissionMapper permissionMapper = Mappers.getMapper(PermissionMapper.class);
        service = new PermissionService(permissionRepository, permissionMapper);
        readPermission = permission(1L, "TEST_SERVICE", READ);
    }

    @Test
    @DisplayName("A unique permission is saved and returned")
    void savePermission_whenUnique_savesAndReturnsIt() {
        when(permissionRepository.findByServiceNameAndAction("TEST_SERVICE", WRITE)).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission(2L, "TEST_SERVICE", WRITE));

        PermissionDto result = service.savePermission(request("TEST_SERVICE", "WRITE"));

        assertEquals(2L, result.getId());
        assertEquals("WRITE", result.getAction());
    }

    @Test
    @DisplayName("A duplicate permission is rejected")
    void savePermission_whenDuplicate_isRejected() {
        when(permissionRepository.findByServiceNameAndAction("TEST_SERVICE", READ)).thenReturn(Optional.of(readPermission));

        assertThrows(PermissionAlreadyExistsException.class, () -> service.savePermission(request("TEST_SERVICE", "READ")));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Updating changes the fields but keeps the id")
    void updatePermission_updatesFieldsButKeepsId() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(readPermission));
        when(permissionRepository.findByServiceNameAndAction("OTHER_SERVICE", WRITE)).thenReturn(Optional.empty());
        when(permissionRepository.save(readPermission)).thenReturn(readPermission);

        PermissionDto result = service.updatePermission(1L, request("OTHER_SERVICE", "WRITE"));

        assertEquals(1L, result.getId());
        assertEquals("OTHER_SERVICE", result.getServiceName());
        assertEquals("WRITE", result.getAction());
    }

    @Test
    @DisplayName("Updating a permission to its own values is allowed")
    void updatePermission_toItsOwnValues_isAllowed() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(readPermission));
        when(permissionRepository.findByServiceNameAndAction("TEST_SERVICE", READ)).thenReturn(Optional.of(readPermission));
        when(permissionRepository.save(readPermission)).thenReturn(readPermission);

        PermissionDto result = service.updatePermission(1L, request("TEST_SERVICE", "READ"));

        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("Updating to the values of another permission is rejected")
    void updatePermission_toAnotherPermissionsValues_isRejected() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(readPermission));
        when(permissionRepository.findByServiceNameAndAction("TEST_SERVICE", WRITE))
                .thenReturn(Optional.of(permission(2L, "TEST_SERVICE", WRITE)));

        assertThrows(PermissionAlreadyExistsException.class, () -> service.updatePermission(1L, request("TEST_SERVICE", "WRITE")));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Updating an unknown permission is rejected")
    void updatePermission_whenNotFound_isRejected() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> service.updatePermission(1L, request("TEST_SERVICE", "READ")));
    }

    @Test
    @DisplayName("An existing permission is deleted")
    void deletePermissionById_deletesExistingPermission() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(readPermission));

        service.deletePermissionById(1L);

        verify(permissionRepository).delete(readPermission);
    }

    @Test
    @DisplayName("Deleting an unknown permission is rejected")
    void deletePermissionById_whenNotFound_isRejected() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> service.deletePermissionById(1L));
        verify(permissionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("A permission is found by id")
    void getPermissionById_returnsPermission() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(readPermission));

        PermissionDto result = service.getPermissionById(1L);

        assertEquals("TEST_SERVICE", result.getServiceName());
        assertEquals("READ", result.getAction());
    }

    @Test
    @DisplayName("An unknown permission id is rejected")
    void getPermissionById_whenNotFound_isRejected() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> service.getPermissionById(1L));
    }

    @Test
    @DisplayName("Only the permissions of the given service are returned")
    void getPermissionByServiceName_returnsPermissionsOfThatService() {
        when(permissionRepository.findAllByServiceName("TEST_SERVICE")).thenReturn(List.of(readPermission));

        List<PermissionDto> result = service.getPermissionByServiceName("TEST_SERVICE");

        assertEquals(List.of(1L), result.stream().map(PermissionDto::getId).toList());
    }

    @Test
    @DisplayName("Every permission is returned")
    void getAllPermissions_returnsEveryPermission() {
        when(permissionRepository.findAll()).thenReturn(List.of(readPermission, permission(2L, "TEST_SERVICE", WRITE)));

        assertEquals(2, service.getAllPermissions().size());
    }

    private static PermissionDto request(String serviceName, String action) {
        return new PermissionDto(null, serviceName, action, "description");
    }

    private static Permission permission(Long id, String serviceName, PermissionAction action) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setServiceName(serviceName);
        permission.setAction(action);
        return permission;
    }
}
