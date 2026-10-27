package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.AlreadyExistsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.example.iamsystem.user.service.UserTestData.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFactoryTest {

    private static final int PASSWORD_EXPIRY_DAYS = 90;

    @Mock
    private UserValidator userValidator;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserFactory factory;
    private UserCreationDto request;

    @BeforeEach
    void setUp() {
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        factory = new UserFactory(userValidator, passwordEncoder, userMapper, new PasswordExpirationProperties(PASSWORD_EXPIRY_DAYS));
        request = new UserCreationDto("alice", "Password1!", "Alice", "alice@example.com", null);
    }

    @Test
    @DisplayName("Builds an active user of the given type in the tenant with an encoded password")
    void newUser_buildsActiveUserInTenantWithEncodedPassword() {
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");

        User user = factory.newUser(request, TENANT, UserType.MANAGEMENT);

        assertEquals("alice", user.getUsername());
        assertEquals("Alice", user.getFullName());
        assertEquals("alice@example.com", user.getEmail());
        assertSame(TENANT, user.getTenant());
        assertEquals("encoded", user.getPassword());
        assertTrue(user.isActive());
        assertEquals(UserType.MANAGEMENT, user.getUserType());
        assertFalse(user.isRootUser());
        assertNull(user.getCreatedBy());
        assertEquals(PASSWORD_EXPIRY_DAYS,
                ChronoUnit.DAYS.between(Instant.now(), user.getPasswordExpiryDate().plusSeconds(60)));
    }

    @Test
    @DisplayName("A username or email that isn't available in the tenant is rejected")
    void newUser_whenNotAvailableInTheTenant_isRejected() {
        doThrow(new AlreadyExistsException("Username not available"))
                .when(userValidator).validateAvailable("alice", "alice@example.com", TENANT.getId());

        assertThrows(AlreadyExistsException.class, () -> factory.newUser(request, TENANT, UserType.MANAGEMENT));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("A root user is a management user whose username and email are checked across all tenants")
    void newRootUser_isCheckedGloballyAndIsManagement() {
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");

        User root = factory.newRootUser(request, TENANT);

        verify(userValidator).validateAvailableForRoot("alice", "alice@example.com");
        assertTrue(root.isRootUser());
        assertEquals(UserType.MANAGEMENT, root.getUserType());
        assertSame(TENANT, root.getTenant());
    }

    @Test
    @DisplayName("A root user with a username or email used anywhere is rejected")
    void newRootUser_whenNotAvailableAnywhere_isRejected() {
        doThrow(new AlreadyExistsException("Email not available"))
                .when(userValidator).validateAvailableForRoot("alice", "alice@example.com");

        assertThrows(AlreadyExistsException.class, () -> factory.newRootUser(request, TENANT));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("A password violating the policy is rejected")
    void newUser_whenPasswordViolatesPolicy_isRejected() {
        doThrow(new UserInputNotValidException("policy")).when(userValidator).validatePasswordPolicy("Password1!");

        assertThrows(UserInputNotValidException.class, () -> factory.newUser(request, TENANT, UserType.MANAGEMENT));
        verifyNoInteractions(passwordEncoder);
    }
}
