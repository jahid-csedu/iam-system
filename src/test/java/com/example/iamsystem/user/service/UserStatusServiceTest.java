package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.actor;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

    private static final Long TENANT_ID = TENANT.getId();

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserStatusService service;
    private User root;
    private User member;

    @BeforeEach
    void setUp() {
        service = new UserStatusService(userRepository, accessPolicy, currentUserProvider, Mappers.getMapper(UserMapper.class));
        root = rootUser(1, "root");
        member = user(2, "member");
        member.setActive(true);
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(root));
    }

    @Test
    @DisplayName("Deactivating a user of the tenant marks them inactive, checked by the policy")
    void deactivateUser_marksTheUserInactive() {
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenReturn(member);

        assertFalse(service.deactivateUser(2L).isActive());

        assertFalse(member.isActive());
        verify(accessPolicy).checkCanChangeStatus(actor(root), member);
    }

    @Test
    @DisplayName("Activating makes the user active again, and repeating either change changes nothing")
    void activateUser_marksTheUserActive_andRepeatsAreHarmless() {
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenReturn(member);

        service.deactivateUser(2L);
        assertFalse(service.deactivateUser(2L).isActive());
        assertTrue(service.activateUser(2L).isActive());
        assertTrue(service.activateUser(2L).isActive());
    }

    @Test
    @DisplayName("A refused change leaves the user as they were")
    void changeRefusedByThePolicy_leavesTheUserUnchanged() {
        when(userRepository.getRequiredByIdAndTenantId(1L, TENANT_ID)).thenReturn(root);
        root.setActive(true);
        doThrow(new NoAccessException("refused")).when(accessPolicy).checkCanChangeStatus(actor(root), root);

        assertThrows(NoAccessException.class, () -> service.deactivateUser(1L));

        assertTrue(root.isActive());
    }

    @Test
    @DisplayName("A user outside the tenant is not found")
    void userOutsideTheTenant_isNotFound() {
        when(userRepository.getRequiredByIdAndTenantId(9L, TENANT_ID)).thenThrow(new DataNotFoundException("User not found"));

        assertThrows(DataNotFoundException.class, () -> service.activateUser(9L));
    }
}
