package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static com.example.iamsystem.user.service.UserTestData.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPasswordUpdaterTest {

    @Mock
    private UserValidator userValidator;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;

    private UserPasswordUpdater updater;
    private User member;

    @BeforeEach
    void setUp() {
        updater = new UserPasswordUpdater(userValidator, passwordEncoder, new PasswordExpirationProperties(90), userRepository);
        member = user(2, "member");
    }

    @Test
    @DisplayName("A new password is stored hashed with a fresh expiry date, and any login lockout ends")
    void updatePassword_storesTheHashAndEndsTheLockout() {
        member.setPasswordExpired(true);
        member.setFailedLoginAttempts(5);
        member.setUserLocked(true);
        member.setAccountLockedUntil(Instant.now().plusSeconds(600));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-new");

        updater.updatePassword(member, "NewPassword1!");

        assertEquals("encoded-new", member.getPassword());
        assertTrue(member.getPasswordExpiryDate().isAfter(Instant.now()));
        assertFalse(member.isPasswordExpired());
        assertEquals(0, member.getFailedLoginAttempts());
        assertFalse(member.isUserLocked());
        assertNull(member.getAccountLockedUntil());
        verify(userRepository).save(member);
    }

    @Test
    @DisplayName("A disabled password is replaced by the hash of an unknown random value")
    void disablePassword_replacesTheHash() {
        when(passwordEncoder.encode(any())).thenReturn("encoded-random");

        updater.disablePassword(member);

        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(raw.capture());
        assertTrue(raw.getValue().length() >= 40);
        assertEquals("encoded-random", member.getPassword());
        verify(userRepository).save(member);
    }

    @Test
    @DisplayName("A password violating the policy is rejected and nothing is saved")
    void updatePassword_violatingThePolicy_isRejected() {
        doThrow(new UserInputNotValidException("policy")).when(userValidator).validatePasswordPolicy("weak");

        assertThrows(UserInputNotValidException.class, () -> updater.updatePassword(member, "weak"));
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }
}
