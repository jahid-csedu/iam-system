package com.example.iamsystem.role.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.Test;

import static com.example.iamsystem.role.service.RoleTestData.role;
import static com.example.iamsystem.role.service.RoleTestData.userWithRoles;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleAccessPolicyTest {

    private final RoleAccessPolicy policy = new RoleAccessPolicy();

    private final Role admin = role(10, "ADMIN");
    private final Role viewer = role(11, "VIEWER");

    @Test
    void checkCanModify_roleTheActorDoesNotHold_isAllowed() {
        assertDoesNotThrow(() -> policy.checkCanModify(userWithRoles(viewer), admin));
    }

    @Test
    void checkCanModify_roleTheActorHolds_isDenied() {
        User actor = userWithRoles(admin, viewer);

        NoAccessException exception = assertThrows(NoAccessException.class, () -> policy.checkCanModify(actor, admin));
        assertEquals("Users cannot change a role they hold", exception.getMessage());
    }
}
