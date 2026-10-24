package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.createdBy;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    private static final Long TENANT_ID = TENANT.getId();

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private UserRoleService service;

    private User root;
    private User child;
    private Role admin;
    private Role viewer;

    @BeforeEach
    void setUp() {
        root = rootUser(1, "root");
        child = createdBy(root, 2, "child");
        admin = role(10, "ADMIN");
        viewer = role(11, "VIEWER");
    }

    @Test
    void assignRoles_addsRolesToUser() {
        givenManageableChild();
        when(roleRepository.findAllByIdInAndTenantId(Set.of(10L, 11L), TENANT_ID)).thenReturn(List.of(admin, viewer));

        service.assignRoles(request(10L, 11L));

        verify(accessPolicy).checkCanManageRoles(root, child);
        assertEquals(Set.of(admin, viewer), child.getRoles());
        verify(userRepository).save(child);
    }

    @Test
    void removeRoles_removesOnlyGivenRoles() {
        givenManageableChild();
        child.getRoles().addAll(Set.of(admin, viewer));
        when(roleRepository.findAllByIdInAndTenantId(Set.of(10L), TENANT_ID)).thenReturn(List.of(admin));

        service.removeRoles(request(10L));

        assertEquals(Set.of(viewer), child.getRoles());
        verify(userRepository).save(child);
    }

    @Test
    void assignRoles_whenSomeRolesAreMissingOrInAnotherTenant_changesNothing() {
        givenManageableChild();
        when(roleRepository.findAllByIdInAndTenantId(Set.of(10L, 99L), TENANT_ID)).thenReturn(List.of(admin));

        assertThrows(DataNotFoundException.class, () -> service.assignRoles(request(10L, 99L)));
        assertTrue(child.getRoles().isEmpty());
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignRoles_whenUserNotFound_isRejected() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(root);
        when(userRepository.getRequiredByUsernameAndTenantId("child", TENANT_ID)).thenThrow(new DataNotFoundException("User not found"));

        assertThrows(DataNotFoundException.class, () -> service.assignRoles(request(10L)));
    }

    @Test
    void assignRoles_whenNotAllowed_isDenied() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(root);
        when(userRepository.getRequiredByUsernameAndTenantId("child", TENANT_ID)).thenReturn(child);
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanManageRoles(root, child);

        assertThrows(NoAccessException.class, () -> service.assignRoles(request(10L)));
        verify(roleRepository, never()).findAllByIdInAndTenantId(any(), any());
        verify(userRepository, never()).save(any());
    }

    private void givenManageableChild() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(root);
        when(userRepository.getRequiredByUsernameAndTenantId("child", TENANT_ID)).thenReturn(child);
    }

    private static UserRoleAttachmentDto request(Long... roleIds) {
        UserRoleAttachmentDto request = new UserRoleAttachmentDto();
        request.setUsername("child");
        request.setRoleIds(Set.of(roleIds));
        return request;
    }

    private static Role role(long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }
}
