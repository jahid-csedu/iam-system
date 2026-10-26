package com.example.iamsystem.security.event;

import com.example.iamsystem.security.service.FailedPasswordCounter;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * A successful login clears the user's failed-password count and any expired lock.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener {

    private final FailedPasswordCounter failedPasswordCounter;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof DefaultUserDetails userDetails) {
            failedPasswordCounter.recordSuccess(userDetails.user());
        }
    }
}
