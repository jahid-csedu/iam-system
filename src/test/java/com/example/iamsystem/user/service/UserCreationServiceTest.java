package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserAlreadyExistsException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
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

import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCreationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserFactory userFactory;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserCreationService service;
    private UserCreationDto request;

    @BeforeEach
    void setUp() {
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        service = new UserCreationService(userRepository, tenantRepository, userFactory, currentUserProvider, userMapper);
        request = new UserCreationDto("alice", "Password1!", "Alice", "alice@example.com");
    }

    @Test
    @DisplayName("A new user joins the creator's tenant with the creator recorded")
    void createUser_putsNewUserInCreatorsTenantWithCreatorRecorded() {
        User creator = rootUser(1, "root");
        User newUser = user(2, "alice");
        when(currentUserProvider.requireCurrentUser()).thenReturn(creator);
        when(tenantRepository.getReferenceById(TENANT.getId())).thenReturn(TENANT);
        when(userFactory.newUser(request, TENANT)).thenReturn(newUser);
        when(userRepository.save(newUser)).thenReturn(newUser);

        UserDto result = service.createUser(request);

        assertSame(creator, newUser.getCreatedBy());
        assertEquals("alice", result.getUsername());
        assertEquals(TENANT.getId(), result.getTenantId());
        assertEquals("root", result.getCreatedBy());
    }

    @Test
    @DisplayName("Creating a user without a logged-in user is denied")
    void createUser_whenNobodyLoggedIn_isDenied() {
        when(currentUserProvider.requireCurrentUser()).thenThrow(new NoAccessException("No logged in user found"));

        assertThrows(NoAccessException.class, () -> service.createUser(request));
        verifyNoInteractions(userFactory, userRepository);
    }

    @Test
    @DisplayName("An invalid new user saves nothing")
    void createUser_whenUserIsInvalid_savesNothing() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(rootUser(1, "root"));
        when(tenantRepository.getReferenceById(TENANT.getId())).thenReturn(TENANT);
        when(userFactory.newUser(request, TENANT)).thenThrow(new UserAlreadyExistsException("Username not available"));

        assertThrows(UserAlreadyExistsException.class, () -> service.createUser(request));
        verify(userRepository, never()).save(any());
    }
}
