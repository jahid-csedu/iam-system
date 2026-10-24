package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.UserAlreadyExistsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
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
        request = new UserCreationDto("alice", "Password1!", "Alice", "alice@example.com");
    }

    @Test
    @DisplayName("Builds an active user in the tenant with an encoded password")
    void newUser_buildsActiveUserInTenantWithEncodedPassword() {
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");

        User user = factory.newUser(request, TENANT);

        assertEquals("alice", user.getUsername());
        assertEquals("Alice", user.getFullName());
        assertEquals("alice@example.com", user.getEmail());
        assertSame(TENANT, user.getTenant());
        assertEquals("encoded", user.getPassword());
        assertTrue(user.isActive());
        assertFalse(user.isRootUser());
        assertNull(user.getCreatedBy());
        assertEquals(PASSWORD_EXPIRY_DAYS,
                ChronoUnit.DAYS.between(Instant.now(), user.getPasswordExpiryDate().plusSeconds(60)));
    }

    @Test
    @DisplayName("A taken username is rejected")
    void newUser_whenUsernameTaken_isRejected() {
        doThrow(new UserAlreadyExistsException("Username not available")).when(userValidator).validateUsernameAvailable("alice");

        assertThrows(UserAlreadyExistsException.class, () -> factory.newUser(request, TENANT));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("A taken email is rejected")
    void newUser_whenEmailTaken_isRejected() {
        doThrow(new UserAlreadyExistsException("Email not available")).when(userValidator).validateEmailAvailable("alice@example.com");

        assertThrows(UserAlreadyExistsException.class, () -> factory.newUser(request, TENANT));
    }

    @Test
    @DisplayName("A password violating the policy is rejected")
    void newUser_whenPasswordViolatesPolicy_isRejected() {
        doThrow(new UserInputNotValidException("policy")).when(userValidator).validatePasswordPolicy("Password1!");

        assertThrows(UserInputNotValidException.class, () -> factory.newUser(request, TENANT));
        verifyNoInteractions(passwordEncoder);
    }
}
