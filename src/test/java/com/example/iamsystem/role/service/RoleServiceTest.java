package com.example.iamsystem.role.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.RoleAlreadyExistsException;
import com.example.iamsystem.role.dto.RoleDto;
import com.example.iamsystem.role.dto.RoleRequestDto;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.mapper.RoleMapper;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.example.iamsystem.role.service.RoleTestData.TENANT;
import static com.example.iamsystem.role.service.RoleTestData.role;
import static com.example.iamsystem.role.service.RoleTestData.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final Long TENANT_ID = TENANT.getId();

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private RoleAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private RoleService service;
    private AuthenticatedActor actor;
    private Role role;
    private RoleRequestDto request;

    @BeforeEach
    void setUp() {
        service = new RoleService(roleRepository, tenantRepository, accessPolicy, currentUserProvider,
                Mappers.getMapper(RoleMapper.class));
        actor = actor();
        role = role(10, "VIEWER");
        request = new RoleRequestDto("AUDITOR", "Reads audit data");
    }

    @Test
    @DisplayName("A role is created in the current tenant")
    void createRole_createsTheRoleInTheCurrentTenant() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(tenantRepository.getReferenceById(TENANT_ID)).thenReturn(TENANT);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDto result = service.createRole(request);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertSame(TENANT, captor.getValue().getTenant());
        assertEquals("AUDITOR", result.name());
        assertEquals("Reads audit data", result.description());
    }

    @Test
    @DisplayName("A role name already used in the tenant is rejected")
    void createRole_withNameTakenInTheTenant_isRejected() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(roleRepository.existsByNameAndTenantId("AUDITOR", TENANT_ID)).thenReturn(true);

        assertThrows(RoleAlreadyExistsException.class, () -> service.createRole(request));
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Updating changes the name and description")
    void updateRole_updatesNameAndDescription() {
        givenActorAndRole();
        when(roleRepository.save(role)).thenReturn(role);

        RoleDto result = service.updateRole(10L, request);

        verify(accessPolicy).checkCanModify(actor, role);
        assertEquals("AUDITOR", result.name());
        assertEquals("Reads audit data", role.getDescription());
    }

    @Test
    @DisplayName("Renaming to another role's name in the tenant is rejected")
    void updateRole_toNameOfAnotherRoleInTheTenant_isRejected() {
        givenActorAndRole();
        when(roleRepository.existsByNameAndTenantIdAndIdNot("AUDITOR", TENANT_ID, 10L)).thenReturn(true);

        assertThrows(RoleAlreadyExistsException.class, () -> service.updateRole(10L, request));
        assertEquals("VIEWER", role.getName());
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("A denied update changes nothing")
    void updateRole_whenNotAllowed_changesNothing() {
        givenActorAndRole();
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanModify(actor, role);

        assertThrows(NoAccessException.class, () -> service.updateRole(10L, request));
        assertEquals("VIEWER", role.getName());
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Updating a role outside the tenant is not found")
    void updateRole_outsideTheTenant_isNotFound() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor);
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenThrow(new DataNotFoundException("Role not found"));

        assertThrows(DataNotFoundException.class, () -> service.updateRole(10L, request));
    }

    @Test
    @DisplayName("An allowed delete deletes the role")
    void deleteRole_whenAllowed_deletesRole() {
        givenActorAndRole();

        service.deleteRole(10L);

        verify(accessPolicy).checkCanModify(actor, role);
        verify(roleRepository).delete(role);
    }

    @Test
    @DisplayName("A denied delete keeps the role")
    void deleteRole_whenNotAllowed_keepsRole() {
        givenActorAndRole();
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanModify(actor, role);

        assertThrows(NoAccessException.class, () -> service.deleteRole(10L));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("A role is looked up by id within the current tenant")
    void getRole_looksUpWithinTheCurrentTenant() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenReturn(role);

        assertEquals("VIEWER", service.getRole(10L).name());
    }

    @Test
    @DisplayName("A role is looked up by name within the current tenant")
    void getRoleByName_looksUpWithinTheCurrentTenant() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(roleRepository.getRequiredByNameAndTenantId("VIEWER", TENANT_ID)).thenReturn(role);

        assertEquals(10L, service.getRoleByName("VIEWER").id());
    }

    @Test
    @DisplayName("Only the tenant's roles are listed")
    void getRoles_returnsTheTenantsRoles() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(roleRepository.findAllByTenantIdOrderByName(TENANT_ID)).thenReturn(List.of(role));

        assertEquals(List.of("VIEWER"), service.getRoles().stream().map(RoleDto::name).toList());
    }

    private void givenActorAndRole() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor);
        when(roleRepository.getRequiredByIdAndTenantId(10L, TENANT_ID)).thenReturn(role);
    }
}
