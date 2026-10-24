package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.permission.entity.Permission;
import com.example.iamsystem.permission.repository.PermissionRepository;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserPermissionAttachmentDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.actor;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTest {

    private static final Long TENANT_ID = TENANT.getId();

    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private UserPermissionService service;

    private User root;
    private User manager;
    private Permission usersRead;
    private Permission usersCreate;

    @BeforeEach
    void setUp() {
        root = rootUser(1, "root");
        manager = user(2, "manager");
        usersRead = mock(Permission.class);
        usersCreate = mock(Permission.class);
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(root));
    }

    @Test
    @DisplayName("Granted permissions are added to the management user")
    void assignPermissions_addsThemToTheUser() {
        when(userRepository.getRequiredByUsernameAndTenantId("manager", TENANT_ID)).thenReturn(manager);
        when(permissionRepository.getRequiredTenantPermissions(Set.of(1L, 2L))).thenReturn(List.of(usersRead, usersCreate));

        service.assignPermissions(new UserPermissionAttachmentDto("manager", Set.of(1L, 2L)));

        verify(accessPolicy).checkCanManageAccess(actor(root), manager);
        verify(accessPolicy).checkCanReceiveAccess(manager);
        assertEquals(Set.of(usersRead, usersCreate), manager.getPermissions());
        verify(userRepository).save(manager);
    }

    @Test
    @DisplayName("Removing permissions removes only the given ones")
    void removePermissions_removesOnlyTheGivenOnes() {
        manager.getPermissions().addAll(Set.of(usersRead, usersCreate));
        when(userRepository.getRequiredByUsernameAndTenantId("manager", TENANT_ID)).thenReturn(manager);
        when(permissionRepository.getRequiredTenantPermissions(Set.of(1L))).thenReturn(List.of(usersRead));

        service.removePermissions(new UserPermissionAttachmentDto("manager", Set.of(1L)));

        assertEquals(Set.of(usersCreate), manager.getPermissions());
        verify(userRepository).save(manager);
    }

    @Test
    @DisplayName("Changing your own permissions is denied")
    void assignPermissions_toSelf_isDenied() {
        when(userRepository.getRequiredByUsernameAndTenantId("root", TENANT_ID)).thenReturn(root);
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanManageAccess(actor(root), root);

        assertThrows(NoAccessException.class, () -> service.assignPermissions(new UserPermissionAttachmentDto("root", Set.of(1L))));
        verifyNoInteractions(permissionRepository);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A regular user cannot be given permissions")
    void assignPermissions_toRegularUser_isRejected() {
        when(userRepository.getRequiredByUsernameAndTenantId("manager", TENANT_ID)).thenReturn(manager);
        doThrow(new UserInputNotValidException("regular")).when(accessPolicy).checkCanReceiveAccess(manager);

        assertThrows(UserInputNotValidException.class,
                () -> service.assignPermissions(new UserPermissionAttachmentDto("manager", Set.of(1L))));
        assertTrue(manager.getPermissions().isEmpty());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unknown or non-TENANT permissions change nothing")
    void assignPermissions_withMissingPermissions_changesNothing() {
        when(userRepository.getRequiredByUsernameAndTenantId("manager", TENANT_ID)).thenReturn(manager);
        when(permissionRepository.getRequiredTenantPermissions(Set.of(99L))).thenThrow(new DataNotFoundException("missing"));

        assertThrows(DataNotFoundException.class,
                () -> service.assignPermissions(new UserPermissionAttachmentDto("manager", Set.of(99L))));
        verify(userRepository, never()).save(any());
    }
}
