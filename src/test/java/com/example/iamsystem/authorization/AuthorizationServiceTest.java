package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.example.iamsystem.permission.entity.PermissionAction.READ;
import static com.example.iamsystem.permission.entity.PermissionAction.WRITE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationServiceTest {

    private final AuthorizationService authorizationService = new AuthorizationService();

    private User user;

    @BeforeEach
    void setUp() {
        Role reader = new Role();
        reader.setId(1L);
        reader.setPermissions(Set.of(permission(1L, "IAM", READ), permission(2L, "BILLING", WRITE)));

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setRoles(Set.of(reader));
    }

    @Test
    @DisplayName("A role granting the service and action gives the permission")
    void hasPermission_whenARoleGrantsServiceAndAction_isTrue() {
        assertTrue(authorizationService.hasPermission(user, "IAM", READ));
        assertTrue(authorizationService.hasPermission(user, "BILLING", WRITE));
    }

    @Test
    @DisplayName("Both the service and the action must match")
    void hasPermission_requiresBothServiceAndActionToMatch() {
        assertFalse(authorizationService.hasPermission(user, "IAM", WRITE));
        assertFalse(authorizationService.hasPermission(user, "BILLING", READ));
        assertFalse(authorizationService.hasPermission(user, "OTHER", READ));
    }

    @Test
    @DisplayName("The root user always has the permission")
    void hasPermission_forRootUser_isAlwaysTrue() {
        User root = new User();
        root.setRootUser(true);

        assertTrue(authorizationService.hasPermission(root, "ANY_SERVICE", PermissionAction.DELETE));
    }

    @Test
    @DisplayName("The check throws only when the permission is missing")
    void checkPermission_throwsOnlyWhenPermissionMissing() {
        assertDoesNotThrow(() -> authorizationService.checkPermission(user, "IAM", READ));
        assertThrows(NoAccessException.class, () -> authorizationService.checkPermission(user, "IAM", WRITE));
    }

    private static Permission permission(Long id, String serviceName, PermissionAction action) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setServiceName(serviceName);
        permission.setAction(action);
        return permission;
    }
}
