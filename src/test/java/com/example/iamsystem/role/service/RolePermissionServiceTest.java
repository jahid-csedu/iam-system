package com.example.iamsystem.role.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.role.dto.RolePermissionDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.permission.entity.PermissionScope.TENANT;
import static com.example.iamsystem.role.service.RoleTestData.role;
import static com.example.iamsystem.role.service.RoleTestData.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    private static final Long TENANT_ID = RoleTestData.TENANT.getId();

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RoleAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private RolePermissionService service;

    private AuthenticatedActor actor;
    private Role role;
    private Permission read;
    private Permission write;

    @BeforeEach
    void setUp() {
        actor = actor();
        role = role(10, "VIEWER");
        read = permission();
        write = permission();
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor);
    }

    @Test
    @DisplayName("Assigned permissions are added to the role")
    void assignPermissions_addsThemToTheRole() {
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenReturn(role);
        when(permissionRepository.findAllByIdInAndScope(Set.of(1L, 2L), TENANT)).thenReturn(List.of(read, write));

        service.assignPermissions(new RolePermissionDto(10L, Set.of(1L, 2L)));

        verify(accessPolicy).checkCanModify(actor, role);
        assertEquals(Set.of(read, write), role.getPermissions());
        verify(roleRepository).save(role);
    }

    @Test
    @DisplayName("Removing permissions removes only the given ones")
    void removePermissions_removesOnlyTheGivenOnes() {
        role.getPermissions().addAll(Set.of(read, write));
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenReturn(role);
        when(permissionRepository.findAllByIdInAndScope(Set.of(1L), TENANT)).thenReturn(List.of(read));

        service.removePermissions(new RolePermissionDto(10L, Set.of(1L)));

        assertEquals(Set.of(write), role.getPermissions());
        verify(roleRepository).save(role);
    }

    @Test
    @DisplayName("If some permissions are missing or not TENANT-scoped, nothing changes")
    void assignPermissions_whenSomePermissionsAreMissingOrNotTenantScoped_changesNothing() {
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenReturn(role);
        when(permissionRepository.findAllByIdInAndScope(Set.of(1L, 99L), TENANT)).thenReturn(List.of(read));

        assertThrows(DataNotFoundException.class, () -> service.assignPermissions(new RolePermissionDto(10L, Set.of(1L, 99L))));
        assertTrue(role.getPermissions().isEmpty());
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("A role outside the tenant is not found")
    void assignPermissions_toRoleOutsideTheTenant_isNotFound() {
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenThrow(new DataNotFoundException("Role not found"));

        assertThrows(DataNotFoundException.class, () -> service.assignPermissions(new RolePermissionDto(10L, Set.of(1L))));
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Assigning permissions to a role the actor holds is denied")
    void assignPermissions_toRoleTheActorHolds_isDenied() {
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenReturn(role);
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanModify(actor, role);

        assertThrows(NoAccessException.class, () -> service.assignPermissions(new RolePermissionDto(10L, Set.of(1L))));
        verify(permissionRepository, never()).findAllByIdInAndScope(any(), any());
        verify(roleRepository, never()).save(any());
    }

    private static Permission permission() {
        return mock(Permission.class);
    }
}
