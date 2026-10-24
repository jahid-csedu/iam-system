package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.permission.service.PermissionService;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.user.service.UserTestData.createdBy;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessPolicyTest {

    @Mock
    private PermissionService permissionService;

    private UserAccessPolicy policy;

    private User root;
    private User child;
    private User grandChild;
    private User stranger;

    @BeforeEach
    void setUp() {
        policy = new UserAccessPolicy(permissionService);
        root = rootUser(1, "root");
        child = createdBy(root, 2, "child");
        grandChild = createdBy(child, 3, "grandchild");
        stranger = user(4, "stranger");
    }

    @Test
    void canView_selfAndDescendants_butNotAncestorsOrStrangers() {
        assertTrue(policy.canView(root, root));
        assertTrue(policy.canView(root, child));
        assertTrue(policy.canView(root, grandChild));
        assertFalse(policy.canView(child, root));
        assertFalse(policy.canView(root, stranger));
    }

    @Test
    void checkCanView_outsideHierarchy_reportsNotFound() {
        assertDoesNotThrow(() -> policy.checkCanView(root, grandChild));
        assertThrows(DataNotFoundException.class, () -> policy.checkCanView(child, root));
    }

    @Test
    void checkCanCreateUsers_requiresWritePermission() {
        when(permissionService.hasPermission("IAM:WRITE")).thenReturn(true);
        assertDoesNotThrow(() -> policy.checkCanCreateUsers(root));

        when(permissionService.hasPermission("IAM:WRITE")).thenReturn(false);
        assertThrows(NoAccessException.class, () -> policy.checkCanCreateUsers(root));
    }

    @Test
    void checkCanManageRoles_withinHierarchyAndWithUpdatePermission_isAllowed() {
        when(permissionService.hasPermission("IAM:UPDATE")).thenReturn(true);

        assertDoesNotThrow(() -> policy.checkCanManageRoles(root, child));
    }

    @Test
    void checkCanManageRoles_withoutUpdatePermission_isDenied() {
        when(permissionService.hasPermission("IAM:UPDATE")).thenReturn(false);

        assertThrows(NoAccessException.class, () -> policy.checkCanManageRoles(root, child));
    }

    @Test
    void checkCanManageRoles_outsideHierarchy_isDeniedBeforeCheckingPermissions() {
        assertThrows(NoAccessException.class, () -> policy.checkCanManageRoles(child, root));
        verifyNoInteractions(permissionService);
    }

    @Test
    void checkCanChangePassword_ownPassword_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanChangePassword(child, child));
    }

    @Test
    void checkCanChangePassword_rootForSubordinate_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanChangePassword(root, grandChild));
    }

    @Test
    void checkCanChangePassword_rootForNonSubordinate_isDenied() {
        assertThrows(NoAccessException.class, () -> policy.checkCanChangePassword(root, stranger));
    }

    @Test
    void checkCanChangePassword_nonRootForSubordinate_isDenied() {
        assertThrows(NoAccessException.class, () -> policy.checkCanChangePassword(child, grandChild));
    }

    @Test
    void checkCanDelete_rootUser_onlyBySelf() {
        User otherRoot = rootUser(5, "other-root");

        assertDoesNotThrow(() -> policy.checkCanDelete(root, root));
        assertThrows(NoAccessException.class, () -> policy.checkCanDelete(otherRoot, root));
    }

    @Test
    void checkCanDelete_nonRootUser_byAnyoneAboveInHierarchy() {
        assertDoesNotThrow(() -> policy.checkCanDelete(root, grandChild));
        assertDoesNotThrow(() -> policy.checkCanDelete(child, grandChild));
        assertThrows(NoAccessException.class, () -> policy.checkCanDelete(stranger, grandChild));
    }
}
