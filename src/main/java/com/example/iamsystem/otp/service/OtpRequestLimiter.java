package com.example.iamsystem.otp.service;

import com.example.iamsystem.otp.entity.OtpPurpose;
import com.example.iamsystem.otp.entity.OtpRequest;
import com.example.iamsystem.otp.repository.OtpRequestRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Allows a user at most {@value #MAX_REQUESTS_PER_WINDOW} one-time codes of each purpose per hour. With
 * {@link OneTimeCodes#MAX_FAILED_ATTEMPTS} guesses per code, that caps the guesses against a user's codes per hour,
 * without ever locking the account (anyone could lock anyone out otherwise).
 */
@Component
@RequiredArgsConstructor
public class OtpRequestLimiter {

    static final int MAX_REQUESTS_PER_WINDOW = 3;
    static final Duration WINDOW = Duration.ofHours(1);

    private final OtpRequestRepository requestRepository;
    private final UserRepository userRepository;

    /**
     * Records the request and returns {@code true} while the user is under the limit for the purpose; over it, records
     * nothing and returns {@code false}. Runs in its own short transaction: the user's row is locked while counting, so
     * parallel requests are counted one after another, and the lock is released as soon as this method returns, before the
     * caller makes the code and sends the email (otherwise the user's logins would wait for the email). A recorded request
     * counts even if the caller fails later.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryRecord(User user, OtpPurpose purpose) {
        userRepository.lockById(user.getId());
        Instant now = Instant.now();
        Instant windowStart = now.minus(WINDOW);
        requestRepository.deleteOlderThan(user, windowStart);
        if (requestRepository.countByUserAndPurposeAndRequestedAtAfter(user, purpose, windowStart) >= MAX_REQUESTS_PER_WINDOW) {
            return false;
        }
        requestRepository.save(new OtpRequest(user, purpose, now));
        return true;
    }
}
