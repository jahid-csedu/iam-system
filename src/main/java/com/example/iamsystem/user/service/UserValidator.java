package com.example.iamsystem.user.service;

import com.example.iamsystem.common.exception.AlreadyExistsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.example.iamsystem.common.exception.ErrorMessage.EMAIL_NOT_AVAILABLE;
import static com.example.iamsystem.common.exception.ErrorMessage.PASSWORD_POLICY_VIOLATION;
import static com.example.iamsystem.common.exception.ErrorMessage.USERNAME_NOT_AVAILABLE;

/**
 * Username/email availability and the password policy.
 * <p>
 * Usernames and emails are unique per tenant, with one exception: a root user's username and email are unique across
 * all users of all tenants. So a new user may not reuse any root's username or email, and a new root may not reuse
 * anyone's.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserValidator {

    /** The special characters are exactly {@code @#!$%^&-+=()}; the hyphen is escaped so it isn't read as a range. */
    private static final Pattern PASSWORD_POLICY =
            Pattern.compile("^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[@#!$%^&\\-+=()])(?=\\S+$).{8,}$");

    private final UserRepository userRepository;

    public void validateAvailable(String username, String email, Long tenantId) {
        if (userRepository.existsByUsernameAndTenantId(username, tenantId) || userRepository.existsByUsernameAndIsRootUserTrue(username)) {
            reject(USERNAME_NOT_AVAILABLE, username);
        }
        validateEmailAvailable(email, tenantId);
    }

    /**
     * For a user who isn't a root: no other user of the tenant and no root of any tenant has this email.
     */
    public void validateEmailAvailable(String email, Long tenantId) {
        if (email != null
                && (userRepository.existsByEmailAndTenantId(email, tenantId) || userRepository.existsByEmailAndIsRootUserTrue(email))) {
            reject(EMAIL_NOT_AVAILABLE, email);
        }
    }

    public void validateAvailableForRoot(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            reject(USERNAME_NOT_AVAILABLE, username);
        }
        validateEmailAvailableForRoot(email);
    }

    /**
     * For a root user: no other user of any tenant has this email.
     */
    public void validateEmailAvailableForRoot(String email) {
        if (email != null && userRepository.existsByEmail(email)) {
            reject(EMAIL_NOT_AVAILABLE, email);
        }
    }

    public void validatePasswordPolicy(String password) {
        if (!PASSWORD_POLICY.matcher(password).matches()) {
            throw new UserInputNotValidException(PASSWORD_POLICY_VIOLATION);
        }
    }

    private static void reject(String message, String value) {
        log.warn("{}: {}", message, value);
        throw new AlreadyExistsException(message);
    }
}
