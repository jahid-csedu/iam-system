package com.example.iamsystem.user.util;

import com.example.iamsystem.exception.UserAlreadyExistsException;
import com.example.iamsystem.exception.UserInputNotValidException;
import com.example.iamsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.iamsystem.constant.ErrorMessage.PASSWORD_POLICY_VIOLATION;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserValidator {
    private final UserRepository userRepository;

    private static final String PASSWORD_PATTERN = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[@#!$%^&-+=()])(?=\\S+$).{8,}$";
    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

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

    public void validatePasswordPolicy(String password) {
        Matcher matcher = pattern.matcher(password);
        if (!matcher.matches()) {
            log.warn("Password policy violation: {}", PASSWORD_POLICY_VIOLATION);
            throw new UserInputNotValidException(PASSWORD_POLICY_VIOLATION);
        }
        log.debug("Password policy validated successfully.");
    }

    private boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
