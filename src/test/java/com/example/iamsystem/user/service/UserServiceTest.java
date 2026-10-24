package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.actor;
import static com.example.iamsystem.user.service.UserTestData.createdBy;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long TENANT_ID = TENANT.getId();

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserService service;
    private User root;
    private User child;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, accessPolicy, currentUserProvider, Mappers.getMapper(UserMapper.class));
        root = rootUser(1, "root");
        child = createdBy(root, 2, "child");
    }

    @Test
    @DisplayName("Returns one page of the tenant's users ordered by id")
    void findUsers_returnsOnePageOfTheTenantsUsersOrderedById() {
        PageRequest expectedRequest = PageRequest.of(1, 2, Sort.by("id"));
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(userRepository.findAllByTenantId(TENANT_ID, expectedRequest))
                .thenReturn(new PageImpl<>(List.of(root, child), expectedRequest, 5));

        Page<UserDto> result = service.findUsers(1, 2);

        assertEquals(List.of("root", "child"), result.getContent().stream().map(UserDto::getUsername).toList());
        assertEquals(5, result.getTotalElements());
    }

    @Test
    @DisplayName("The page size is capped")
    void findUsers_capsThePageSize() {
        PageRequest cappedRequest = PageRequest.of(0, UserService.MAX_PAGE_SIZE, Sort.by("id"));
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(userRepository.findAllByTenantId(TENANT_ID, cappedRequest)).thenReturn(Page.empty(cappedRequest));

        assertEquals(UserService.MAX_PAGE_SIZE, service.findUsers(0, 10_000).getSize());
    }

    @Test
    @DisplayName("A negative page or a page size below 1 is rejected")
    void findUsers_withInvalidPaging_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.findUsers(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.findUsers(0, 0));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("A user is looked up by id within the current tenant")
    void findUserById_looksUpWithinTheCurrentTenant() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenReturn(child);

        UserDto result = service.findUserById(2L);

        assertEquals("child", result.getUsername());
        assertEquals("root", result.getCreatedBy());
    }

    @Test
    @DisplayName("A user outside the tenant is not found")
    void findUserById_outsideTheTenant_isNotFound() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenThrow(new DataNotFoundException("User not found"));

        assertThrows(DataNotFoundException.class, () -> service.findUserById(2L));
    }

    @Test
    @DisplayName("A user is looked up by username within the current tenant")
    void findUserByUsername_looksUpWithinTheCurrentTenant() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(userRepository.getRequiredByUsernameAndTenantId("child", TENANT_ID)).thenReturn(child);

        assertEquals("child", service.findUserByUsername("child").getUsername());
    }

    @Test
    @DisplayName("A user is looked up by email within the current tenant")
    void findUserByEmail_looksUpWithinTheCurrentTenant() {
        when(currentUserProvider.requireCurrentTenantId()).thenReturn(TENANT_ID);
        when(userRepository.getRequiredByEmailAndTenantId("child@example.com", TENANT_ID)).thenReturn(child);

        assertEquals("child@example.com", service.findUserByEmail("child@example.com").getEmail());
    }

    @Test
    @DisplayName("An allowed delete deletes the user")
    void deleteUser_whenAllowed_deletesUser() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(root));
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenReturn(child);

        service.deleteUser(2L);

        verify(accessPolicy).checkCanDelete(actor(root), child);
        verify(userRepository).delete(child);
    }

    @Test
    @DisplayName("A denied delete keeps the user")
    void deleteUser_whenNotAllowed_keepsUser() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(root));
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenReturn(child);
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanDelete(actor(root), child);

        assertThrows(NoAccessException.class, () -> service.deleteUser(2L));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Deleting a user outside the tenant is not found")
    void deleteUser_outsideTheTenant_isNotFound() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(root));
        when(userRepository.getRequiredByIdAndTenantId(2L, TENANT_ID)).thenThrow(new DataNotFoundException("User not found"));

        assertThrows(DataNotFoundException.class, () -> service.deleteUser(2L));
        verify(userRepository, never()).delete(any());
    }
}
