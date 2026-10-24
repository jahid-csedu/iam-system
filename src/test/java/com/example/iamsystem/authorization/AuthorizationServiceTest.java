package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.permission.entity.PermissionAction.DELETE;
import static com.example.iamsystem.permission.entity.PermissionAction.READ;
import static com.example.iamsystem.permission.entity.PermissionAction.WRITE;
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

    private static final AuthenticatedActor ALICE = new AuthenticatedActor(1L, "alice", 100L, false);
    private static final AuthenticatedActor ROOT = new AuthenticatedActor(2L, "root", 100L, true);

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    @Test
    @DisplayName("A permission granted through one of the user's roles is held")
    void hasPermission_whenGrantedThroughARole_isTrue() {
        when(permissionRepository.isGrantedToUser(1L, "IAM", READ)).thenReturn(true);

        assertTrue(authorizationService.hasPermission(ALICE, "IAM", READ));
    }

    @Test
    @DisplayName("A permission not granted through any role is not held")
    void hasPermission_whenNotGranted_isFalse() {
        when(permissionRepository.isGrantedToUser(1L, "IAM", WRITE)).thenReturn(false);

        assertFalse(authorizationService.hasPermission(ALICE, "IAM", WRITE));
    }

    @Test
    @DisplayName("The root user always has the permission, without a database lookup")
    void hasPermission_forRootUser_isAlwaysTrue() {
        assertTrue(authorizationService.hasPermission(ROOT, "ANY_SERVICE", DELETE));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("The check throws only when the permission is missing")
    void checkPermission_throwsOnlyWhenPermissionMissing() {
        when(permissionRepository.isGrantedToUser(1L, "IAM", READ)).thenReturn(true);
        when(permissionRepository.isGrantedToUser(1L, "IAM", WRITE)).thenReturn(false);

        assertDoesNotThrow(() -> authorizationService.checkPermission(ALICE, "IAM", READ));
        assertThrows(NoAccessException.class, () -> authorizationService.checkPermission(ALICE, "IAM", WRITE));
    }
}
