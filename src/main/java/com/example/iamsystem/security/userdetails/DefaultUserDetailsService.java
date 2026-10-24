package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.USER_NOT_FOUND;

/**
 * Loads users for login (by username inside the client's tenant) and for token refresh (by id).
 */
@Service
@RequiredArgsConstructor
public class DefaultUserDetailsService {

    private final AccountStatusUserDetailsChecker accountStatusChecker = new AccountStatusUserDetailsChecker();
    private final UserRepository userRepository;

    public Optional<DefaultUserDetails> findInTenant(String username, Long tenantId) {
        return userRepository.findByUsernameAndTenantId(username, tenantId).map(DefaultUserDetails::new);
    }

    /**
     * The user behind a refresh token, rejected while locked, disabled or with an expired password.
     */
    public DefaultUserDetails requireUsableUser(Long userId) {
        DefaultUserDetails userDetails = userRepository.findById(userId)
                .map(DefaultUserDetails::new)
                .orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));
        accountStatusChecker.check(userDetails);
        return userDetails;
    }
}
