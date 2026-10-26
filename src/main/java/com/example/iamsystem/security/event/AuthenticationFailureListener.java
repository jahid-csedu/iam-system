package com.example.iamsystem.security.event;

import com.example.iamsystem.security.service.FailedPasswordCounter;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Counts failed logins (wrong password) through {@link FailedPasswordCounter}, which locks the account after too many.
 * The login request carries the tenant id as its details, so the user is found inside that tenant.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationFailureListener {

    private final UserRepository userRepository;
    private final FailedPasswordCounter failedPasswordCounter;

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        Authentication request = event.getAuthentication();
        if (!(request.getDetails() instanceof Long tenantId)) {
            return;
        }
        userRepository.findByUsernameAndTenantId(request.getName(), tenantId)
                .ifPresent(user -> failedPasswordCounter.recordFailure(user.getId()));
    }
}
