package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.UserAlreadyExistsException;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserValidatorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserValidator userValidator;


    @Test
    @DisplayName("A taken email is rejected")
    void validateEmailAvailable_whenEmailExists_shouldThrowException() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class, () -> userValidator.validateEmailAvailable(email));

        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("A free email is accepted")
    void validateEmailAvailable_whenEmailDoesNotExist_shouldNotThrowException() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        userValidator.validateEmailAvailable(email);

        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("A taken username is rejected")
    void validateUsernameAvailable_whenUsernameExists_shouldThrowException() {
        // Arrange
        String username = "testuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(new User()));

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class, () -> userValidator.validateUsernameAvailable(username));

        verify(userRepository, times(1)).findByUsername(username);
    }

    @Test
    @DisplayName("A free username is accepted")
    void validateUsernameAvailable_whenUsernameDoesNotExist_shouldNotThrowException() {
        // Arrange
        String username = "testuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        userValidator.validateUsernameAvailable(username);

        verify(userRepository, times(1)).findByUsername(username);
    }
}