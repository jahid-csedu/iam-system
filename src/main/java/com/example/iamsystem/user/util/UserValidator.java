package com.example.iamsystem.user.util;

import com.example.iamsystem.exception.UserAlreadyExistsException;
import com.example.iamsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserValidator {
    private final UserRepository userRepository;

    public void validateEmailAvailable(String email) {
        if(emailExists(email)) {
            log.error("Email already exists: {}", email);
            throw new UserAlreadyExistsException("Email not available");
        }
    }

    public void validateUsernameAvailable(String username) {
        if(userExists(username)){
            log.error("Username already exists: {}", username);
            throw new UserAlreadyExistsException("Username not available");
        }
    }

    private boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
