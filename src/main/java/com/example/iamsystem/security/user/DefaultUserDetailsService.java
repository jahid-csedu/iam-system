package com.example.iamsystem.security.user;

import com.example.iamsystem.user.model.entity.User;
import com.example.iamsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

import static com.example.iamsystem.constant.ErrorMessage.ACCOUNT_LOCKED;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Attempting to load user by username: {}", username);
        Optional<User> userOptional = repository.findByUsername(username);
        if (userOptional.isEmpty()) {
            log.warn("User not found with username: {}", username);
            throw new UsernameNotFoundException("User doesn't exist");
        }
        User user = userOptional.get();

        if (user.isUserLocked() && user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isAfter(Instant.now())) {
            log.warn("Account for user '{}' is locked until: {}", username, user.getAccountLockedUntil());
            throw new LockedException(ACCOUNT_LOCKED);
        }

        log.info("User '{}' loaded successfully.", username);
        return new DefaultUserDetails(user);
    }
}
