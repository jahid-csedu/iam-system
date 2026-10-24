package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import static com.example.iamsystem.common.exception.ErrorMessage.ACCOUNT_LOCKED;

/**
 * Loads users for login and token refresh. A locked account is rejected here, so refresh is blocked as well as login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    @Override
    public DefaultUserDetails loadUserByUsername(String username) {
        DefaultUserDetails userDetails = repository.findByUsername(username)
                .map(DefaultUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User doesn't exist"));
        if (!userDetails.isAccountNonLocked()) {
            log.warn("Account of user '{}' is locked until {}", username, userDetails.user().getAccountLockedUntil());
            throw new LockedException(ACCOUNT_LOCKED);
        }
        return userDetails;
    }
}
