package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.iamsystem.user.service.UserTestData.actor;
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
    @DisplayName("Managing another user's roles and permissions is allowed")
    void checkCanManageAccess_ofAnotherUser_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanManageAccess(actor(manager), member));
        assertDoesNotThrow(() -> policy.checkCanManageAccess(actor(member), manager));
        assertDoesNotThrow(() -> policy.checkCanManageAccess(actor(root), manager));
    }

    @Test
    @DisplayName("Managing your own roles and permissions is denied")
    void checkCanManageAccess_ofSelf_isDenied() {
        NoAccessException exception = assertThrows(NoAccessException.class, () -> policy.checkCanManageAccess(actor(manager), manager));
        assertEquals("Users cannot change their own roles or permissions", exception.getMessage());
        assertThrows(NoAccessException.class, () -> policy.checkCanManageAccess(actor(root), root));
    }

    @Test
    @DisplayName("Only management users can receive roles or permissions")
    void checkCanReceiveAccess_onlyForManagementUsers() {
        manager.setUserType(UserType.MANAGEMENT);

        assertDoesNotThrow(() -> policy.checkCanReceiveAccess(manager));
        UserInputNotValidException exception = assertThrows(UserInputNotValidException.class,
                () -> policy.checkCanReceiveAccess(member));
        assertEquals("Regular users cannot hold roles or permissions", exception.getMessage());
    }

    @Test
    @DisplayName("Resetting another non-root user's password is allowed")
    void checkCanResetPassword_ofAnotherUser_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanResetPassword(actor(manager), member));
        assertDoesNotThrow(() -> policy.checkCanResetPassword(actor(root), manager));
    }

    @Test
    @DisplayName("Resetting your own password is denied; you change it instead")
    void checkCanResetPassword_ofSelf_isDenied() {
        NoAccessException exception = assertThrows(NoAccessException.class,
                () -> policy.checkCanResetPassword(actor(manager), manager));
        assertEquals("Users cannot reset their own password; change it instead", exception.getMessage());
    }

    @Test
    @DisplayName("Nobody can reset the root user's password")
    void checkCanResetPassword_ofRoot_isDenied() {
        assertThrows(NoAccessException.class, () -> policy.checkCanResetPassword(actor(manager), root));
        assertThrows(NoAccessException.class, () -> policy.checkCanResetPassword(actor(root), root));
    }

    @Test
    @DisplayName("Nobody deletes the root user, not even the root itself")
    void checkCanDelete_rootUser_isRefused() {
        assertThrows(NoAccessException.class, () -> policy.checkCanDelete(actor(root), root));
        assertThrows(NoAccessException.class, () -> policy.checkCanDelete(actor(manager), root));
    }

    @Test
    @DisplayName("A non-root user may be deleted")
    void checkCanDelete_nonRootUser_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanDelete(actor(manager), member));
        assertDoesNotThrow(() -> policy.checkCanDelete(actor(root), manager));
    }
}
