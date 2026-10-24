package com.example.iamsystem.security.event;

import com.example.iamsystem.security.config.LockoutProperties;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Counts failed logins (wrong password) of a user and locks the account after too many.
 * The login request carries the tenant id as its details, so the user is found inside that tenant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFailureListener {

    private final UserRepository userRepository;
    private final LockoutProperties lockout;

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        Authentication request = event.getAuthentication();
        if (!(request.getDetails() instanceof Long tenantId)) {
            return;
        }
        userRepository.findByUsernameAndTenantId(request.getName(), tenantId).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            userRepository.updateFailedLoginAttempts(user.getId(), attempts);
            if (attempts >= lockout.maxFailedAttempts()) {
                Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(lockout.lockoutDurationMinutes()));
                userRepository.updateAccountLockStatus(user.getId(), true, lockedUntil);
                log.warn("Account of user '{}' (id {}) locked until {}", user.getUsername(), user.getId(), lockedUntil);
            }
        });
    }
}
