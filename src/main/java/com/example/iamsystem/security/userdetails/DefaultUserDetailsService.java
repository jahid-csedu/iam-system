package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Loads users for login (by username inside the client's tenant) and checks the user behind a token refresh.
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
     * Rejects the user behind a refresh token while locked, disabled or with an expired password.
     */
    public void checkUsable(User user) {
        accountStatusChecker.check(new DefaultUserDetails(user));
    }
}
