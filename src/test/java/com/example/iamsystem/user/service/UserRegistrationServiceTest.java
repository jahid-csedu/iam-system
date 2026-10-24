package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserAlreadyExistsException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.dto.UserRegistrationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;

import static com.example.iamsystem.user.service.UserTestData.rootUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    private static final int PASSWORD_EXPIRY_DAYS = 90;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserValidator userValidator;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserRegistrationService service;
    private UserRegistrationDto request;

    @BeforeEach
    void setUp() {
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        service = new UserRegistrationService(userRepository, userValidator, accessPolicy, currentUserProvider,
                passwordEncoder, userMapper, new PasswordExpirationProperties(PASSWORD_EXPIRY_DAYS));
        request = new UserRegistrationDto("newuser", "Password1!", "New User", "new@example.com", false, new HashSet<>());
    }

    @Test
    void registerRootUser_whenNobodyLoggedIn_savesRootUserWithEncodedPassword() {
        request.setRootUser(true);
        when(currentUserProvider.currentUser()).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = service.registerUser(request);

        User saved = savedUser();
        assertTrue(saved.isRootUser());
        assertEquals("encoded", saved.getPassword());
        assertNull(saved.getCreatedBy());
        assertTrue(saved.isActive());
        assertExpiresInConfiguredDays(saved.getPasswordExpiryDate());
        assertEquals("newuser", result.getUsername());
    }

    @Test
    void registerRootUser_whenSomeoneLoggedIn_isDenied() {
        request.setRootUser(true);
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(rootUser(1, "root")));

        assertThrows(NoAccessException.class, () -> service.registerUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_byPermittedCreator_setsCreator() {
        User creator = rootUser(1, "root");
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(creator));
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = service.registerUser(request);

        verify(accessPolicy).checkCanCreateUsers(creator);
        assertEquals(creator, savedUser().getCreatedBy());
        assertEquals("root", result.getCreatedBy());
    }

    @Test
    void registerUser_whenNobodyLoggedIn_isDenied() {
        when(currentUserProvider.currentUser()).thenReturn(Optional.empty());

        assertThrows(NoAccessException.class, () -> service.registerUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_whenCreatorLacksPermission_isDenied() {
        User creator = rootUser(1, "root");
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(creator));
        doThrow(new NoAccessException("denied")).when(accessPolicy).checkCanCreateUsers(creator);

        assertThrows(NoAccessException.class, () -> service.registerUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_whenUsernameTaken_failsBeforeAnythingElse() {
        doThrow(new UserAlreadyExistsException("Username not available"))
                .when(userValidator).validateUsernameAvailable("newuser");

        assertThrows(UserAlreadyExistsException.class, () -> service.registerUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_whenEmailTaken_isRejected() {
        doThrow(new UserAlreadyExistsException("Email not available"))
                .when(userValidator).validateEmailAvailable("new@example.com");

        assertThrows(UserAlreadyExistsException.class, () -> service.registerUser(request));
        verify(userRepository, never()).save(any());
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertExpiresInConfiguredDays(Instant expiry) {
        long days = ChronoUnit.DAYS.between(Instant.now(), expiry.plusSeconds(60));
        assertEquals(PASSWORD_EXPIRY_DAYS, days);
    }
}
