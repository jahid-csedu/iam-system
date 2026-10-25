package com.example.iamsystem.user.service;

import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserAlreadyExistsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.permission.PermissionKey.USERS_CREATE_MANAGEMENT;
import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static com.example.iamsystem.user.service.UserTestData.actor;
import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static com.example.iamsystem.user.service.UserTestData.tenant;
import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    @Mock
    private AuthorizationService authorizationService;

    private UserCreationService service;
    private UserCreationDto request;

    @BeforeEach
    void setUp() {
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        service = new UserCreationService(userRepository, tenantRepository, userFactory, authorizationService, currentUserProvider,
                userMapper);
        request = new UserCreationDto("alice", "Password1!", "Alice", "alice@example.com", null);
    }

    @Test
    @DisplayName("The platform tenant refuses regular users")
    void createUser_regularUserInPlatformTenant_isRefused() {
        Tenant platform = tenant(1L, "Platform");
        platform.setType(TenantType.PLATFORM);
        User platformRoot = rootUser(1, "platform-root");
        platformRoot.setTenant(platform);
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(platformRoot));
        when(tenantRepository.getReferenceById(1L)).thenReturn(platform);

        assertThrows(UserInputNotValidException.class, () -> service.createUser(request));
        verifyNoInteractions(userFactory);
    }

    @Test
    @DisplayName("A new user is REGULAR by default and joins the creator's tenant with the creator recorded")
    void createUser_putsNewUserInCreatorsTenantWithCreatorRecorded() {
        User creator = rootUser(1, "root");
        User newUser = user(2, "alice");
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(creator));
        when(tenantRepository.getReferenceById(TENANT.getId())).thenReturn(TENANT);
        when(userRepository.getReferenceById(1L)).thenReturn(creator);
        when(userFactory.newUser(request, TENANT, UserType.REGULAR)).thenReturn(newUser);
        when(userRepository.save(newUser)).thenReturn(newUser);

        UserDto result = service.createUser(request);

        assertSame(creator, newUser.getCreatedBy());
        assertEquals("alice", result.getUsername());
        assertEquals(TENANT.getId(), result.getTenantId());
        assertEquals("root", result.getCreatedBy());
        verifyNoInteractions(authorizationService);
    }

    @Test
    @DisplayName("A management user is created only with users:create-management")
    void createUser_management_requiresTheManagementKey() {
        UserCreationDto managementRequest = new UserCreationDto("bob", "Password1!", "Bob", "bob@example.com", UserType.MANAGEMENT);
        User creator = rootUser(1, "root");
        User newUser = user(3, "bob");
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(creator));
        when(tenantRepository.getReferenceById(TENANT.getId())).thenReturn(TENANT);
        when(userRepository.getReferenceById(1L)).thenReturn(creator);
        when(userFactory.newUser(managementRequest, TENANT, UserType.MANAGEMENT)).thenReturn(newUser);
        when(userRepository.save(newUser)).thenReturn(newUser);

        service.createUser(managementRequest);

        verify(authorizationService).checkPermission(actor(creator), USERS_CREATE_MANAGEMENT);
    }

    @Test
    @DisplayName("Without users:create-management, no management user is created")
    void createUser_managementWithoutTheKey_createsNothing() {
        UserCreationDto managementRequest = new UserCreationDto("bob", "Password1!", "Bob", "bob@example.com", UserType.MANAGEMENT);
        User creator = user(1, "manager");
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(creator));
        doThrow(new NoAccessException("denied")).when(authorizationService).checkPermission(actor(creator), USERS_CREATE_MANAGEMENT);

        assertThrows(NoAccessException.class, () -> service.createUser(managementRequest));
        verifyNoInteractions(userFactory);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Creating a user without a logged-in user is denied")
    void createUser_whenNobodyLoggedIn_isDenied() {
        when(currentUserProvider.requireCurrentActor()).thenThrow(new NoAccessException("No logged in user found"));

        assertThrows(NoAccessException.class, () -> service.createUser(request));
        verifyNoInteractions(userFactory, userRepository);
    }

    @Test
    @DisplayName("An invalid new user saves nothing")
    void createUser_whenUserIsInvalid_savesNothing() {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor(rootUser(1, "root")));
        when(tenantRepository.getReferenceById(TENANT.getId())).thenReturn(TENANT);
        when(userFactory.newUser(request, TENANT, UserType.REGULAR)).thenThrow(new UserAlreadyExistsException("Username not available"));

        assertThrows(UserAlreadyExistsException.class, () -> service.createUser(request));
        verify(userRepository, never()).save(any());
    }
}
