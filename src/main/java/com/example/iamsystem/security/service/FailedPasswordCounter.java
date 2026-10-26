package com.example.iamsystem.security.service;

import com.example.iamsystem.security.config.LockoutProperties;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * The one place that counts wrong passwords: at login, and when a logged-in user proves who they are (old password on a
 * password change, current password on an email change). After {@code security.max-failed-attempts} the account is
 * locked for {@code security.lockout-duration-minutes}: no login, and the JWT filter refuses its tokens, so a stolen
 * access token can't be used to guess the password either.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FailedPasswordCounter {

    private final UserRepository userRepository;
    private final LockoutProperties lockout;

    /**
     * Counts in a transaction of its own, so the count stays when the caller's request fails and rolls back. The
     * increment is one {@code UPDATE}, so parallel wrong guesses are all counted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        userRepository.incrementFailedLoginAttempts(userId);
        if (userRepository.findFailedLoginAttempts(userId) >= lockout.maxFailedAttempts()) {
            Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(lockout.lockoutDurationMinutes()));
            userRepository.updateAccountLockStatus(userId, true, lockedUntil);
            log.warn("Account of user {} locked until {} after too many wrong passwords", userId, lockedUntil);
        }
    }

    /**
     * A correct password clears the count and any expired lock, on the given entity too, so a later save of it inside
     * the caller's transaction writes the same values.
     */
    @Transactional
    public void recordSuccess(User user) {
        boolean counted = user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0;
        if (counted || user.isUserLocked()) {
            userRepository.clearFailedLogins(user.getId());
            user.setFailedLoginAttempts(0);
            user.setUserLocked(false);
            user.setAccountLockedUntil(null);
        }
    }
}
