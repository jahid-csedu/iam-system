package com.example.iamsystem.role.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.role.service.RoleTestData.actor;
import static com.example.iamsystem.role.service.RoleTestData.role;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAccessPolicyTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleAccessPolicy policy;

    private final Role admin = role(10, "ADMIN");

    @Test
    @DisplayName("A role the actor doesn't hold may be changed")
    void checkCanModify_roleTheActorDoesNotHold_isAllowed() {
        when(userRepository.existsByIdAndRolesId(1L, 10L)).thenReturn(false);

        assertDoesNotThrow(() -> policy.checkCanModify(actor(), admin));
    }

    @Test
    @DisplayName("A role the actor holds may not be changed")
    void checkCanModify_roleTheActorHolds_isDenied() {
        when(userRepository.existsByIdAndRolesId(1L, 10L)).thenReturn(true);

        NoAccessException exception = assertThrows(NoAccessException.class, () -> policy.checkCanModify(actor(), admin));
        assertEquals("Users cannot change a role they hold", exception.getMessage());
    }
}
