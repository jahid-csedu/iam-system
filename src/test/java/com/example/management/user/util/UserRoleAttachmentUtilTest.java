package com.example.management.user.util;

import com.example.management.exception.DataNotFoundException;
import com.example.management.role.Role;
import com.example.management.role.RoleRepository;
import com.example.management.user.model.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleAttachmentUtilTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserRoleAttachmentUtil userRoleAttachmentUtil;


    @Test
    void validateAndRetrieveRoles_whenAllRolesExist_shouldReturnRoles() {
        // Arrange
        Set<Long> roleIds = new HashSet<>(List.of(1L, 2L));
        Role role1 = new Role();
        role1.setId(1L);
        Role role2 = new Role();
        role2.setId(2L);
        when(roleRepository.findAllById(roleIds)).thenReturn(List.of(role1, role2));

        // Act
        Set<Role> roles = userRoleAttachmentUtil.validateAndRetrieveRoles(roleIds);

        // Assert
        assertTrue(roles.contains(role1));
        assertTrue(roles.contains(role2));
        assertEquals(roles.size(), roleIds.size());
        verify(roleRepository, times(1)).findAllById(roleIds);
    }

    @Test
    void validateAndRetrieveRoles_whenSomeRolesDoNotExist_shouldThrowException() {
        // Arrange
        Set<Long> roleIds = new HashSet<>(List.of(1L, 2L));
        Role role1 = new Role();
        role1.setId(1L);
        when(roleRepository.findAllById(roleIds)).thenReturn(List.of(role1));

        // Act & Assert
        assertThrows(DataNotFoundException.class, () -> userRoleAttachmentUtil.validateAndRetrieveRoles(roleIds));
        verify(roleRepository, times(1)).findAllById(roleIds);
    }

    @Test
    void assignRolesToUser_shouldAssignRolesToUser() {
        // Arrange
        User user = new User();
        Set<Role> roles = new HashSet<>(List.of(new Role()));

        // Act
        userRoleAttachmentUtil.assignRolesToUser(user, roles);

        // Assert
        assertTrue(user.getRoles().containsAll(roles));
    }
}