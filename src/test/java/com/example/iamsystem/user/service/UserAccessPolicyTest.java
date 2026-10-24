package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.Test;

import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserAccessPolicyTest {

    private final UserAccessPolicy policy = new UserAccessPolicy();

    private final User root = rootUser(1, "root");
    private final User manager = user(2, "manager");
    private final User member = user(3, "member");

    @Test
    void checkCanManageRoles_ofAnotherUser_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanManageRoles(manager, member));
        assertDoesNotThrow(() -> policy.checkCanManageRoles(member, manager));
        assertDoesNotThrow(() -> policy.checkCanManageRoles(root, manager));
    }

    @Test
    void checkCanManageRoles_ofSelf_isDenied() {
        NoAccessException exception = assertThrows(NoAccessException.class, () -> policy.checkCanManageRoles(manager, manager));
        assertEquals("Users cannot change their own roles", exception.getMessage());
        assertThrows(NoAccessException.class, () -> policy.checkCanManageRoles(root, root));
    }

    @Test
    void checkCanChangePassword_ownPassword_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanChangePassword(member, member));
    }

    @Test
    void checkCanChangePassword_rootForAnyoneElse_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanChangePassword(root, member));
    }

    @Test
    void checkCanChangePassword_nonRootForSomeoneElse_isDenied() {
        assertThrows(NoAccessException.class, () -> policy.checkCanChangePassword(manager, member));
        assertThrows(NoAccessException.class, () -> policy.checkCanChangePassword(manager, root));
    }

    @Test
    void checkCanDelete_rootUser_onlyBySelf() {
        assertDoesNotThrow(() -> policy.checkCanDelete(root, root));
        assertThrows(NoAccessException.class, () -> policy.checkCanDelete(manager, root));
    }

    @Test
    void checkCanDelete_nonRootUser_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanDelete(manager, member));
        assertDoesNotThrow(() -> policy.checkCanDelete(root, manager));
    }
}
