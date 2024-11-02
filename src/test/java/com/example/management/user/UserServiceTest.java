package com.example.management.user;

import com.example.management.exception.DataNotFoundException;
import com.example.management.exception.UserAlreadyExistsException;
import com.example.management.permission.Permission;
import com.example.management.role.Role;
import com.example.management.user.model.dto.UserRegistrationDto;
import com.example.management.user.model.entity.User;
import com.example.management.user.util.UserRoleAttachmentUtil;
import com.example.management.user.util.UserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserValidator userValidator;

    @Mock
    private UserRoleAttachmentUtil userRoleAttachmentUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserRegistrationDto userDto;
    private User user;
    private Set<Role> roles;

    @BeforeEach
    public void setUp() {
        userDto = new UserRegistrationDto();
        userDto.setUsername("testUser");
        userDto.setPassword("password");
        userDto.setEmail("test@example.com");
        userDto.setRoleIds(Set.of(1L));

        Role role = new Role();
        role.setId(1L);
        role.setName("ROLE_ADMIN");
        role.setPermissions(Set.of(new Permission(1L, "READ", ""), new Permission(2L, "WRITE", "")));

        roles = Set.of(role);

        user = new User();
        user.setUsername("testUser");
        user.setPassword("encoded_password");
        user.setEmail("test@example.com");
        user.setRoles(roles);
    }

    @Test
    void registerUser_successfulRegistration() {
        // Arrange
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRoleAttachmentUtil.validateAndRetrieveRoles(anySet())).thenReturn(roles);
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        User registeredUser = userService.registerUser(userDto);

        // Assert
        assertNotNull(registeredUser);
        verify(userValidator).validateUsernameAvailable(anyString());
        verify(userValidator).validateEmailAvailable(anyString());
        verify(passwordEncoder).encode(anyString());
        verify(userRoleAttachmentUtil).validateAndRetrieveRoles(anySet());
        verify(userRoleAttachmentUtil).assignRolesToUser(any(User.class), anySet());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_whenUsernameNotAvailable_thenValidationFailure() {
        // Arrange
        doThrow(new UserAlreadyExistsException("Username not available")).when(userValidator).validateUsernameAvailable(anyString());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.registerUser(userDto));
        verify(userValidator).validateUsernameAvailable(anyString());
    }

    @Test
    void registerUser_whenEmailNotAvailable_thenValidationFailure() {
        // Arrange
        doNothing().when(userValidator).validateUsernameAvailable(anyString());
        doThrow(new UserAlreadyExistsException("Email not available")).when(userValidator).validateEmailAvailable(anyString());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.registerUser(userDto));
        verify(userValidator).validateUsernameAvailable(anyString());
    }

    @Test
    void registerUser_roleAssignmentFailure() {
        // Arrange
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRoleAttachmentUtil.validateAndRetrieveRoles(anySet())).thenThrow(new DataNotFoundException("Some roles not found"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.registerUser(userDto));
        verify(userValidator).validateUsernameAvailable(anyString());
        verify(userValidator).validateEmailAvailable(anyString());
        verify(passwordEncoder).encode(anyString());
        verify(userRoleAttachmentUtil).validateAndRetrieveRoles(anySet());
    }
}