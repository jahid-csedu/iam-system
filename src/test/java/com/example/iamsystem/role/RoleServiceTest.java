package com.example.iamsystem.role;

import com.example.iamsystem.audit.AuditService;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.audit.enums.AuditOutcome;
import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.model.PermissionAction;
import com.example.iamsystem.permission.PermissionRepository;
import com.example.iamsystem.role.model.Role;
import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RolePermissionDto;
import com.example.iamsystem.security.user.DefaultUserDetails;
import com.example.iamsystem.user.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleService roleService;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private DefaultUserDetails userDetails;

    private Role role;
    private RoleDto roleDto;
    private Set<Long> permissionIds;
    private List<Permission> permissions;

    @Mock
    private User user;

    @BeforeEach
    void setUp() {
        roleDto = new RoleDto();
        roleDto.setId(1L);
        roleDto.setName("ROLE_USER");
        role = new Role();
        role.setId(1L);
        role.setName("ROLE_USER");

        Permission p1 = new Permission();
        p1.setId(1L);
        p1.setServiceName("TEST");
        p1.setAction(PermissionAction.READ);

        Permission p2 = new Permission();
        p2.setId(2L);
        p2.setServiceName("TEST");
        p2.setAction(PermissionAction.WRITE);

        permissionIds = new HashSet<>(Arrays.asList(1L, 2L));
        permissions = Arrays.asList(p1, p2);

        user = new User();
        user.setId(1L);
        user.setUsername("testUser");
    }

    private void setupSecurityContext() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.user()).thenReturn(user);
    }

    @Test
    void createRole_createsAndReturnsRole() {
        setupSecurityContext();
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(auditService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(new HashMap<>());

        RoleDto result = roleService.createRole(roleDto);

        assertNotNull(result);
        assertEquals(roleDto.getName(), result.getName());
        verify(roleRepository, times(1)).save(any(Role.class));
        verify(auditService, times(1)).logAuditEvent(eq(AuditEventType.ROLE_CREATED), eq("testUser"), eq(role.getName()), eq(AuditOutcome.SUCCESS), anyMap(), anyString(), anyString());
    }

    @Test
    void updateRole_updatesAndReturnsRole() {
        setupSecurityContext();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(auditService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(new HashMap<>());

        RoleDto result = roleService.updateRole(1L, roleDto);

        assertNotNull(result);
        assertEquals(roleDto.getName(), result.getName());
        verify(roleRepository, times(1)).save(any(Role.class));
        verify(auditService, times(1)).logAuditEvent(eq(AuditEventType.ROLE_UPDATED), eq("testUser"), eq(role.getName()), eq(AuditOutcome.SUCCESS), anyMap(), anyString(), anyString());
    }

    @Test
    void deleteRole_deletesRole() {
        setupSecurityContext();
        doNothing().when(roleRepository).deleteById(1L);
        when(auditService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(new HashMap<>());

        roleService.deleteRole(1L);

        verify(roleRepository, times(1)).deleteById(1L);
        verify(auditService, times(1)).logAuditEvent(eq(AuditEventType.ROLE_DELETED), eq("testUser"), eq("ID: 1"), eq(AuditOutcome.SUCCESS), anyMap(), anyString(), anyString());
    }

    @Test
    void getRole_returnsRole() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        RoleDto result = roleService.getRole(1L);

        assertNotNull(result);
        assertEquals(roleDto.getName(), result.getName());
    }

    @Test
    void getRoleByName_returnsRole() {
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));

        RoleDto result = roleService.getRoleByName("ROLE_USER");

        assertNotNull(result);
        assertEquals(roleDto.getName(), result.getName());
    }

    @Test
    void getRoles_returnsListOfRoles() {
        when(roleRepository.findAll()).thenReturn(Collections.singletonList(role));

        List<RoleDto> result = roleService.getRoles();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void assignPermissions_attachesPermissionsToRole() {
        setupSecurityContext();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(permissionIds)).thenReturn(permissions);
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(auditService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(new HashMap<>());

        RolePermissionDto rolePermissionDto = new RolePermissionDto(1L, permissionIds);
        roleService.assignPermissions(rolePermissionDto);

        verify(permissionRepository, times(1)).findAllById(anySet());
        verify(roleRepository, times(1)).save(any(Role.class));
        verify(auditService, times(1)).logAuditEvent(eq(AuditEventType.PERMISSIONS_ASSIGNED_TO_ROLE), eq("testUser"), eq("ID: 1"), eq(AuditOutcome.SUCCESS), anyMap(), anyString(), anyString());
    }

    @Test
    void assignPermissions_throwsExceptionWhenPermissionsNotFound() {
        setupSecurityContext();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(permissionIds)).thenReturn(Collections.singletonList(permissions.getFirst()));
        when(auditService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(new HashMap<>());

        RolePermissionDto rolePermissionDto = new RolePermissionDto(1L, permissionIds);

        assertThrows(DataNotFoundException.class, () -> roleService.assignPermissions(rolePermissionDto));
        verify(auditService, times(1)).logAuditEvent(eq(AuditEventType.PERMISSIONS_ASSIGNMENT_TO_ROLE_FAILED), eq("testUser"), eq("ID: 1"), eq(AuditOutcome.FAILURE), anyMap(), anyString(), anyString());
    }

    @Test
    void removePermissions_removesPermissionsFromRole() {
        setupSecurityContext();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(permissionIds)).thenReturn(permissions);
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(auditService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(new HashMap<>());

        RolePermissionDto rolePermissionDto = new RolePermissionDto(1L, permissionIds);
        roleService.removePermissions(rolePermissionDto);

        verify(permissionRepository, times(1)).findAllById(anySet());
        verify(roleRepository, times(1)).save(any(Role.class));
        verify(auditService, times(1)).logAuditEvent(eq(AuditEventType.PERMISSIONS_REMOVED_FROM_ROLE), eq("testUser"), eq("ID: 1"), eq(AuditOutcome.SUCCESS), anyMap(), anyString(), anyString());
    }

    @Test
    void removePermissions_throwsExceptionWhenPermissionsNotFound() {
        setupSecurityContext();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(permissionIds)).thenReturn(Collections.singletonList(permissions.get(0)));
        when(auditService.getRequestDetails(any(HttpServletRequest.class))).thenReturn(new HashMap<>());

        RolePermissionDto rolePermissionDto = new RolePermissionDto(1L, permissionIds);

        assertThrows(DataNotFoundException.class, () -> roleService.removePermissions(rolePermissionDto));
        verify(auditService, times(1)).logAuditEvent(eq(AuditEventType.PERMISSIONS_REMOVED_FROM_ROLE_FAILED), eq("testUser"), eq("ID: 1"), eq(AuditOutcome.FAILURE), anyMap(), anyString(), anyString());
    }
}