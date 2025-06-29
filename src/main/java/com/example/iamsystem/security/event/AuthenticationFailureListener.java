package com.example.iamsystem.security.event;

import com.example.iamsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFailureListener {

    private final UserRepository userRepository;

    @Value("${security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${security.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = (String) event.getAuthentication().getPrincipal();
        log.warn("Authentication failed for user: {}", username);

        userRepository.findByUsername(username).ifPresent(user -> {
            int newAttempts = user.getFailedLoginAttempts() + 1;

            userRepository.updateFailedLoginAttempts(username, newAttempts);
            log.debug("User '{}' failed login attempts: {}", username, newAttempts);

            if (newAttempts >= maxFailedAttempts) {
                Instant lockedUntil = Instant.now().plus(lockoutDurationMinutes, ChronoUnit.MINUTES);
                userRepository.updateAccountLockStatus(username, true, lockedUntil);
                log.warn("User '{}' account locked until: {}", username, lockedUntil);
            }
        });
    }
}