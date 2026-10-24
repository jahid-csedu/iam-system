package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.USER_NOT_LOGGED_IN;

/**
 * Single source of truth for the user making the current request.
 */
@Component
public class CurrentUserProvider {

    public Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof DefaultUserDetails userDetails) {
            return Optional.ofNullable(userDetails.user());
        }
        return Optional.empty();
    }

    public User requireCurrentUser() {
        return currentUser().orElseThrow(() -> new NoAccessException(USER_NOT_LOGGED_IN));
    }
}
