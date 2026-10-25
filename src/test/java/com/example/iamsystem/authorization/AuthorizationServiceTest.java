package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.permission.PermissionKey.TENANTS_READ;
import static com.example.iamsystem.permission.PermissionKey.USERS_CREATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_READ;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The permission query itself is exercised by the controller tests against MySQL.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    private static final AuthenticatedActor ALICE = new AuthenticatedActor(1L, "alice", 100L, false, 10L);
    private static final AuthenticatedActor ROOT = new AuthenticatedActor(2L, "root", 100L, true, 20L);

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    @Test
    @DisplayName("A permission granted through one of the user's roles is held")
    void hasPermission_whenGrantedThroughARole_isTrue() {
        when(permissionRepository.isGrantedToUser(1L, "users:read")).thenReturn(true);

        assertTrue(authorizationService.hasPermission(ALICE, USERS_READ));
    }

    @Test
    @DisplayName("A permission not granted through any role is not held")
    void hasPermission_whenNotGranted_isFalse() {
        when(permissionRepository.isGrantedToUser(1L, "users:create")).thenReturn(false);

        assertFalse(authorizationService.hasPermission(ALICE, USERS_CREATE));
    }

    @Test
    @DisplayName("The root user holds every TENANT permission, without a database lookup")
    void hasPermission_forRootUser_holdsTenantPermissions() {
        assertTrue(authorizationService.hasPermission(ROOT, USERS_CREATE));
        verifyNoInteractions(permissionRepository, tenantRepository);
    }

    @Test
    @DisplayName("A standard tenant's root holds no PLATFORM permission")
    void hasPermission_forTenantRoot_holdsNoPlatformPermission() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(false);

        assertFalse(authorizationService.hasPermission(ROOT, TENANTS_READ));
    }

    @Test
    @DisplayName("The platform tenant's root holds every PLATFORM permission")
    void hasPermission_forPlatformRoot_holdsPlatformPermissions() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(true);

        assertTrue(authorizationService.hasPermission(ROOT, TENANTS_READ));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("The check throws only when the permission is missing")
    void checkPermission_throwsOnlyWhenPermissionMissing() {
        when(permissionRepository.isGrantedToUser(1L, "users:read")).thenReturn(true);
        when(permissionRepository.isGrantedToUser(1L, "users:create")).thenReturn(false);

        assertDoesNotThrow(() -> authorizationService.checkPermission(ALICE, USERS_READ));
        assertThrows(NoAccessException.class, () -> authorizationService.checkPermission(ALICE, USERS_CREATE));
    }
}
