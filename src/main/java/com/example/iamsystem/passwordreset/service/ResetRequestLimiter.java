package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.passwordreset.entity.PasswordResetRequest;
import com.example.iamsystem.passwordreset.repository.PasswordResetRequestRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Allows a user at most {@value #MAX_REQUESTS_PER_WINDOW} password-reset OTPs per hour. With
 * {@link PasswordResetService#MAX_FAILED_ATTEMPTS} guesses per OTP, that caps the guesses against a user's OTPs per hour,
 * without ever locking the account (anyone could lock anyone out otherwise).
 */
@Component
@RequiredArgsConstructor
public class ResetRequestLimiter {

    static final int MAX_REQUESTS_PER_WINDOW = 3;
    static final Duration WINDOW = Duration.ofHours(1);

    private final PasswordResetRequestRepository requestRepository;
    private final UserRepository userRepository;

    /**
     * Records the request and returns {@code true} while the user is under the limit; over it, records nothing and
     * returns {@code false}. The user's row stays locked until the caller's transaction ends, so parallel requests are
     * counted one after another.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryRecord(User user) {
        userRepository.lockById(user.getId());
        Instant now = Instant.now();
        Instant windowStart = now.minus(WINDOW);
        requestRepository.deleteOlderThan(user, windowStart);
        if (requestRepository.countByUserAndRequestedAtAfter(user, windowStart) >= MAX_REQUESTS_PER_WINDOW) {
            return false;
        }
        requestRepository.save(new PasswordResetRequest(user, now));
        return true;
    }
}
