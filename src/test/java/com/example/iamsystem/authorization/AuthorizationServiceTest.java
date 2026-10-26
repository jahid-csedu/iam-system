package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.PermissionKey;
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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.example.iamsystem.permission.PermissionKey.CLIENTS_READ;
import static com.example.iamsystem.permission.PermissionKey.TENANTS_CREATE;
import static com.example.iamsystem.permission.PermissionKey.TENANTS_READ;
import static com.example.iamsystem.permission.PermissionKey.TENANTS_SUSPEND;
import static com.example.iamsystem.permission.PermissionKey.USERS_CREATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_READ;
import static com.example.iamsystem.permission.entity.PermissionScope.TENANT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("Inside another tenant the platform root holds every permission")
    void hasPermission_platformRootInAnotherTenant_holdsEverything() {
        AuthenticatedActor acting = new AuthenticatedActor(2L, "platform-root", 1L, true, 20L).actingIn(100L);
        when(tenantRepository.existsByIdAndType(1L, TenantType.PLATFORM)).thenReturn(true);

        assertTrue(authorizationService.hasPermission(acting, USERS_CREATE));
        assertTrue(authorizationService.hasPermission(acting, TENANTS_READ));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("Inside another tenant other platform users hold exactly what their grants on that tenant give them")
    void hasPermission_otherPlatformUserInAnotherTenant_holdsTheirGrants() {
        AuthenticatedActor acting = new AuthenticatedActor(3L, "support", 1L, false, 30L).actingIn(100L);
        when(permissionRepository.isGrantedToPlatformUserInTenant(3L, "users:read", 100L)).thenReturn(true);
        when(permissionRepository.isGrantedToPlatformUserInTenant(3L, "users:create", 100L)).thenReturn(false);

        assertTrue(authorizationService.hasPermission(acting, USERS_READ));
        assertFalse(authorizationService.hasPermission(acting, USERS_CREATE));
        verify(permissionRepository, never()).isGrantedToUser(any(), any());
    }

    @Test
    @DisplayName("Grants never give PLATFORM permissions inside a tenant")
    void hasPermission_otherPlatformUserInAnotherTenant_noPlatformPermissions() {
        AuthenticatedActor acting = new AuthenticatedActor(3L, "support", 1L, false, 30L).actingIn(100L);

        assertFalse(authorizationService.hasPermission(acting, TENANTS_READ));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("A PLATFORM permission in a normally assigned role gives nothing: it takes effect only through grants")
    void hasPermission_platformPermissionThroughNormalRole_isNotHeld() {
        assertFalse(authorizationService.hasPermission(ALICE, TENANTS_READ));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("A tenant's root holds every TENANT permission and no PLATFORM one, without a permission query")
    void permissionsOf_tenantRoot_isEveryTenantKey() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(false);

        Set<PermissionKey> permissions = authorizationService.permissionsOf(ROOT);

        assertEquals(EnumSet.allOf(PermissionKey.class).stream().filter(key -> key.scope() == TENANT).toList(),
                List.copyOf(permissions));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("The platform root holds every permission, at home and inside another tenant")
    void permissionsOf_platformRoot_isEveryKey() {
        AuthenticatedActor acting = new AuthenticatedActor(2L, "platform-root", 100L, true, 20L).actingIn(7L);
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(true);

        assertEquals(EnumSet.allOf(PermissionKey.class), authorizationService.permissionsOf(ROOT));
        assertEquals(EnumSet.allOf(PermissionKey.class), authorizationService.permissionsOf(acting));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("Other users hold the TENANT keys of their roles and direct permissions; PLATFORM keys there count for nothing")
    void permissionsOf_user_isTheTenantKeysOfRolesAndDirectPermissions() {
        when(permissionRepository.findNamesGrantedToUser(1L)).thenReturn(Set.of("users:read", "clients:read", "tenants:read"));

        assertEquals(EnumSet.of(USERS_READ, CLIENTS_READ), authorizationService.permissionsOf(ALICE));
    }

    @Test
    @DisplayName("Inside another tenant other platform users hold the TENANT keys their grants give on it")
    void permissionsOf_platformUserInAnotherTenant_isTheTenantKeysOfTheGrants() {
        AuthenticatedActor acting = new AuthenticatedActor(3L, "support", 1L, false, 30L).actingIn(100L);
        when(permissionRepository.findNamesGrantedToPlatformUserInTenant(3L, 100L)).thenReturn(Set.of("users:read", "tenants:read"));

        assertEquals(EnumSet.of(USERS_READ), authorizationService.permissionsOf(acting));
        verify(permissionRepository, never()).findNamesGrantedToUser(any());
    }

    @Test
    @DisplayName("The platform root holds PLATFORM permissions on all tenants")
    void platformScope_ofPlatformRoot_isAllTenants() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(true);

        assertEquals(TenantScope.ALL, authorizationService.platformScope(ROOT, TENANTS_READ));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("Another platform user holds PLATFORM permissions on the tenants of their grants, or on all with an all-tenants grant")
    void platformScope_ofPlatformUser_followsTheGrants() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(true);
        when(permissionRepository.findGrantedTenantIds(1L, "tenants:read")).thenReturn(Arrays.asList(5L, 9L));
        when(permissionRepository.findGrantedTenantIds(1L, "tenants:suspend")).thenReturn(Arrays.asList(5L, null));

        TenantScope readable = authorizationService.platformScope(ALICE, TENANTS_READ);

        assertEquals(TenantScope.of(Set.of(5L, 9L)), readable);
        assertTrue(readable.covers(9L));
        assertFalse(readable.covers(7L));
        assertTrue(authorizationService.platformScope(ALICE, TENANTS_SUSPEND).allTenants());
    }

    @Test
    @DisplayName("Tenant users never hold PLATFORM permissions, not even a tenant's root")
    void platformScope_ofTenantUser_isDenied() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(false);

        assertThrows(NoAccessException.class, () -> authorizationService.platformScope(ROOT, TENANTS_READ));
        assertThrows(NoAccessException.class, () -> authorizationService.platformScope(ALICE, TENANTS_READ));
    }

    @Test
    @DisplayName("A per-tenant check needs the tenant in scope, and an all-tenants check needs all tenants")
    void platformChecks_followTheScope() {
        when(tenantRepository.existsByIdAndType(100L, TenantType.PLATFORM)).thenReturn(true);
        when(permissionRepository.findGrantedTenantIds(1L, "tenants:create")).thenReturn(List.of(5L));

        assertDoesNotThrow(() -> authorizationService.checkPlatformPermission(ALICE, TENANTS_CREATE, 5L));
        assertThrows(NoAccessException.class, () -> authorizationService.checkPlatformPermission(ALICE, TENANTS_CREATE, 6L));
        assertThrows(NoAccessException.class, () -> authorizationService.checkPlatformPermissionOnAllTenants(ALICE, TENANTS_CREATE));
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
