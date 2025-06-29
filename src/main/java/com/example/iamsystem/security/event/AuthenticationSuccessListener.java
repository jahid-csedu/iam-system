package com.example.iamsystem.security.event;

import com.example.iamsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationSuccessListener {

    private final UserRepository userRepository;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        log.info("Authentication successful for user: {}", username);

        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0 || user.isUserLocked()) {
                userRepository.updateFailedLoginAttempts(username, 0);
                userRepository.updateAccountLockStatus(username, false, null);
                log.info("User '{}' failed login attempts reset and account unlocked.", username);
            }
        });
    }
}