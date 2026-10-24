package com.example.iamsystem.security.event;

import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * A successful login clears the user's failed-login count and any expired lock.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener {

    private final UserRepository userRepository;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof DefaultUserDetails userDetails)) {
            return;
        }
        User user = userDetails.user();
        if (user.getFailedLoginAttempts() > 0 || user.isUserLocked()) {
            userRepository.updateFailedLoginAttempts(user.getId(), 0);
            userRepository.updateAccountLockStatus(user.getId(), false, null);
        }
    }
}
