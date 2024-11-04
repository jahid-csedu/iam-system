package com.example.iamsystem.user.util;

import com.example.iamsystem.exception.UserAlreadyExistsException;
import com.example.iamsystem.user.UserRepository;
import com.example.iamsystem.user.model.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserValidatorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserValidator userValidator;


    @Test
    void validateEmailAvailable_whenEmailExists_shouldThrowException() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class, () -> userValidator.validateEmailAvailable(email));

        // Verify logging behavior
        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    void validateEmailAvailable_whenEmailDoesNotExist_shouldNotThrowException() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        userValidator.validateEmailAvailable(email);

        // Verify logging behavior
        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    void validateUsernameAvailable_whenUsernameExists_shouldThrowException() {
        // Arrange
        String username = "testuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(new User()));

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class, () -> userValidator.validateUsernameAvailable(username));

        // Verify logging behavior
        verify(userRepository, times(1)).findByUsername(username);
    }

    @Test
    void validateUsernameAvailable_whenUsernameDoesNotExist_shouldNotThrowException() {
        // Arrange
        String username = "testuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        userValidator.validateUsernameAvailable(username);

        // Verify logging behavior
        verify(userRepository, times(1)).findByUsername(username);
    }
}