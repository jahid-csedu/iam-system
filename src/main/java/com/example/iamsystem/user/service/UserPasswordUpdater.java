package com.example.iamsystem.user.service;

import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

import static com.example.iamsystem.common.util.DateUtil.calculateExpiryDate;

/**
 * The one place that gives a user a new password they chose: it checks the password policy, stores the hash with a fresh
 * expiry date, and ends any login lockout (the caller has just made the user prove who they are, with the old password
 * or a reset OTP), and can switch a password off. Which sessions end is up to the caller.
 */
@Component
@RequiredArgsConstructor
public class UserPasswordUpdater {

    private static final int UNKNOWABLE_PASSWORD_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final PasswordExpirationProperties passwordExpiration;
    private final UserRepository userRepository;

    public void updatePassword(User user, String newPassword) {
        userValidator.validatePasswordPolicy(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordExpiryDate(calculateExpiryDate(passwordExpiration.days()));
        user.setPasswordExpired(false);
        user.setFailedLoginAttempts(0);
        user.setUserLocked(false);
        user.setAccountLockedUntil(null);
        userRepository.save(user);
    }

    /**
     * The user's current password stops working: it is replaced by the hash of a random value nobody knows, so only a
     * password reset gets the user back in.
     */
    public void disablePassword(User user) {
        byte[] unknowable = new byte[UNKNOWABLE_PASSWORD_BYTES];
        random.nextBytes(unknowable);
        user.setPassword(passwordEncoder.encode(Base64.getEncoder().encodeToString(unknowable)));
        userRepository.save(user);
    }
}
