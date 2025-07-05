package com.example.iamsystem.permission;

import com.example.iamsystem.audit.AuditService;
import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.permission.model.Permission;
import com.example.iamsystem.permission.model.PermissionAction;
import com.example.iamsystem.permission.model.PermissionDto;
import com.example.iamsystem.role.model.Role;
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

import java.time.Instant;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private DefaultUserDetails userDetails;

    private Permission permission;
    private PermissionDto permissionDto;

    @Mock
    private User user;
    @Mock
    private Role role;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest request;

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

        role = new Role();
        role.setId(1L);
        role.setName("ROLE_USER");
        role.setPermissions(Set.of(permission));

        user = new User();
        user.setId(1L);
        user.setUsername("testUser");
        user.setPassword("encoded_password");
        user.setEmail("test@example.com");
        user.setPasswordExpiryDate(Instant.now().plusSeconds(1000));
        user.setRoles(Set.of(role));
    }

    private void setupSecurityContext() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.user()).thenReturn(user);
    }

    @Test
    void savePermission_savesAndReturnsPermission() {
        when(permissionRepository.findByServiceNameAndAction(any(String.class), any(PermissionAction.class))).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenReturn(permission);

        PermissionDto result = permissionService.savePermission(permissionDto);

        assertNotNull(result);
        assertEquals(permissionDto.getServiceName(), result.getServiceName());
        assertEquals(permissionDto.getAction(), result.getAction());
        verify(permissionRepository, times(1)).save(any(Permission.class));
    }

    @Test
    void savePermission_throwsExceptionWhenPermissionExists() {
        when(permissionRepository.findByServiceNameAndAction(any(String.class), any(PermissionAction.class))).thenReturn(Optional.of(permission));

        assertThrows(com.example.iamsystem.exception.PermissionAlreadyExistsException.class, () -> permissionService.savePermission(permissionDto));
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
    void deletePermissionById_throwsExceptionWhenNotFound() {
        doThrow(new DataNotFoundException("Permission not found")).when(permissionRepository).deleteById(1L);

        assertThrows(DataNotFoundException.class, () -> permissionService.deletePermissionById(1L));
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
        setupSecurityContext();
        assertTrue(permissionService.hasPermission("TEST_SERVICE:READ"));
    }

    @Test
    void testHasPermission_whenUserDoesNotHaveProvidedPermission_thenReturnFalse() {
        setupSecurityContext();
        assertFalse(permissionService.hasPermission("TEST_SERVICE:WRITE"));
    }

    @Test
    void testHasPermission_whenUserIsRootUser_thenReturnTrue() {
        user.setRootUser(true);
        setupSecurityContext();
        assertTrue(permissionService.hasPermission("ANY_SERVICE:ANY_ACTION"));
    }
}