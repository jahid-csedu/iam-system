package com.example.iamsystem.user;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.UserAlreadyExistsException;
import com.example.iamsystem.permission.Permission;
import com.example.iamsystem.role.Role;
import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.model.entity.User;
import com.example.iamsystem.user.util.UserRoleAttachmentUtil;
import com.example.iamsystem.user.util.UserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private UserRegistrationDto userRegistrationDto;
    private User user;
    private UserDto userDto;
    private Set<Role> roles;

    @BeforeEach
    public void setUp() {
        userRegistrationDto = new UserRegistrationDto();
        userRegistrationDto.setUsername("testUser");
        userRegistrationDto.setPassword("password");
        userRegistrationDto.setEmail("test@example.com");
        userRegistrationDto.setRoleIds(Set.of(1L));

        Permission p1 = new Permission();
        p1.setId(1L);
        p1.setServiceName("TEST");
        p1.setAction("READ");

        Permission p2 = new Permission();
        p2.setId(2L);
        p2.setServiceName("TEST");
        p2.setAction("WRITE");

        Role role = new Role();
        role.setId(1L);
        role.setName("ROLE_ADMIN");
        role.setPermissions(Set.of(p1, p2));

        roles = Set.of(role);

        user = new User();
        user.setId(1L);
        user.setUsername("testUser");
        user.setPassword("encoded_password");
        user.setEmail("test@example.com");
        user.setRoles(roles);

        userDto = new UserDto();
        userDto.setId(1L);
        userDto.setUsername("testUser");
        userDto.setEmail("test@example.com");
        userDto.setRoleIds(Set.of(1L));
    }

    @Test
    void registerUser_successfulRegistration() {
        // Arrange
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        User registeredUser = userService.registerUser(userRegistrationDto);

        // Assert
        assertNotNull(registeredUser);
        verify(userValidator).validateUsernameAvailable(anyString());
        verify(userValidator).validateEmailAvailable(anyString());
        verify(passwordEncoder).encode(anyString());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_whenUsernameNotAvailable_thenValidationFailure() {
        // Arrange
        doThrow(new UserAlreadyExistsException("Username not available")).when(userValidator).validateUsernameAvailable(anyString());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.registerUser(userRegistrationDto));
        verify(userValidator).validateUsernameAvailable(anyString());
    }

    @Test
    void registerUser_whenEmailNotAvailable_thenValidationFailure() {
        // Arrange
        doNothing().when(userValidator).validateUsernameAvailable(anyString());
        doThrow(new UserAlreadyExistsException("Email not available")).when(userValidator).validateEmailAvailable(anyString());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.registerUser(userRegistrationDto));
        verify(userValidator).validateUsernameAvailable(anyString());
    }

    @Test
    void assignRoles_successfulAttachment() {
        // Arrange
        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("testUser");
        Set<Long> roleIds = Set.of(1L);
        userRoleAttachmentDto.setRoleIds(roleIds);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.ofNullable(user));
        when(userRoleAttachmentUtil.validateAndRetrieveRoles(anySet())).thenReturn(roles);
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act & Assert
        userService.assignRoles(userRoleAttachmentDto);
        verify(userRoleAttachmentUtil).validateAndRetrieveRoles(anySet());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void assignRoles_whenUserNotFound_AttachmentFailure() {
        // Arrange
        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("testUser");
        Set<Long> roleIds = Set.of(1L);
        userRoleAttachmentDto.setRoleIds(roleIds);
        doThrow(new DataNotFoundException("User not found")).when(userRepository).findByUsername(anyString());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.assignRoles(userRoleAttachmentDto));
    }

    @Test
    void assignRoles_whenRoleNotFound_AttachmentFailure() {
        // Arrange
        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("testUser");
        Set<Long> roleIds = Set.of(1L);
        userRoleAttachmentDto.setRoleIds(roleIds);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.ofNullable(user));
        doThrow(new DataNotFoundException("Some roles not found")).when(userRoleAttachmentUtil).validateAndRetrieveRoles(anySet());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userService.assignRoles(userRoleAttachmentDto));
    }

    @Test
    void removeRoles_successfulRemoval() {
        UserRoleAttachmentDto userRoleAttachmentDto = new UserRoleAttachmentDto();
        userRoleAttachmentDto.setUsername("testUser");
        userRoleAttachmentDto.setRoleIds(Set.of(1L));

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
        when(userRoleAttachmentUtil.validateAndRetrieveRoles(anySet())).thenReturn(roles);

        userService.removeRoles(userRoleAttachmentDto);

        verify(userRoleAttachmentUtil).removeRolesFromUser(user, roles);
        verify(userRepository).save(user);
    }

    @Test
    void findAllUsers_successfulRetrieval() {
        List<User> users = List.of(user);
        List<UserDto> userDtos = List.of(userDto);
        when(userRepository.findAll()).thenReturn(users);

        List<UserDto> result = userService.findAllUsers();

        assertEquals(userDtos, result);
        verify(userRepository).findAll();
    }

    @Test
    void findUserById_userFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));

        UserDto result = userService.findUserById(1L);

        assertEquals(userDto, result);
        verify(userRepository).findById(1L);
    }

    @Test
    void findUserById_userNotFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> userService.findUserById(1L));
        verify(userRepository).findById(1L);
    }

    @Test
    void findUserByUsername_userFound() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));

        UserDto result = userService.findUserByUsername("testUser");

        assertEquals(userDto, result);
        verify(userRepository).findByUsername("testUser");
    }

    @Test
    void findUserByUsername_userNotFound() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> userService.findUserByUsername("testUser"));
        verify(userRepository).findByUsername("testUser");
    }

    @Test
    void findUserByEmail_userFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        UserDto result = userService.findUserByEmail("test@example.com");

        assertEquals(userDto, result);
        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void findUserByEmail_userNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> userService.findUserByEmail("test@example.com"));
        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void deleteUser_successfulDeletion() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));
        doNothing().when(userRepository).deleteById(anyLong());

        userService.deleteUser(1L);

        verify(userRepository).findById(1L);
        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_userNotFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> userService.deleteUser(1L));
        verify(userRepository).findById(1L);
    }
}