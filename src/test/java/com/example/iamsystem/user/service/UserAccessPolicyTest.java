package com.example.iamsystem.user.service;

import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.permission.entity.PermissionAction.WRITE;
import static com.example.iamsystem.user.service.UserTestData.createdBy;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserAccessPolicyTest {

    @Mock
    private AuthorizationService authorizationService;

    private UserAccessPolicy policy;

    private User root;
    private User child;
    private User grandChild;
    private User stranger;

    @BeforeEach
    void setUp() {
        policy = new UserAccessPolicy(authorizationService);
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
    void checkCanCreateUsers_requiresIamWritePermission() {
        policy.checkCanCreateUsers(root);

        verify(authorizationService).checkPermission(root, "IAM", WRITE);
    }

    @Test
    void checkCanCreateUsers_withoutPermission_isDenied() {
        doThrow(new NoAccessException("denied")).when(authorizationService).checkPermission(child, "IAM", WRITE);

        assertThrows(NoAccessException.class, () -> policy.checkCanCreateUsers(child));
    }

    @Test
    void checkCanManageRoles_withinHierarchy_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanManageRoles(root, child));
        assertDoesNotThrow(() -> policy.checkCanManageRoles(child, grandChild));
    }

    @Test
    void checkCanManageRoles_outsideHierarchy_isDenied() {
        assertThrows(NoAccessException.class, () -> policy.checkCanManageRoles(child, root));
        assertThrows(NoAccessException.class, () -> policy.checkCanManageRoles(root, stranger));
        verifyNoInteractions(authorizationService);
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
