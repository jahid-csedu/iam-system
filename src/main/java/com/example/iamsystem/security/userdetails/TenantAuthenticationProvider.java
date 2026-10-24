package com.example.iamsystem.security.userdetails;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Authenticates a username and password inside one tenant: the login request carries the tenant id (resolved from the
 * client) as its details. Spring's account checks (locked, disabled, expired) and authentication events apply as usual,
 * and an unknown username looks exactly like a wrong password.
 */
@Component
@RequiredArgsConstructor
public class TenantAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

    private static final String BAD_CREDENTIALS = "Bad credentials";
    private static final String UNKNOWN_USER_PASSWORD = "not-a-real-password";

    private final DefaultUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    private String unknownUserPasswordHash;

    @Override
    protected UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) {
        if (!(authentication.getDetails() instanceof Long tenantId)) {
            throw new InternalAuthenticationServiceException("Login request without a tenant");
        }
        return userDetailsService.findInTenant(username, tenantId).orElseThrow(() -> {
            spendPasswordCheckTime(authentication);
            return new UsernameNotFoundException(BAD_CREDENTIALS);
        });
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) {
        Object password = authentication.getCredentials();
        if (password == null || !passwordEncoder.matches(password.toString(), userDetails.getPassword())) {
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
    }

    /**
     * Checks the password against a dummy hash, so an unknown username takes as long as a wrong password.
     */
    private void spendPasswordCheckTime(UsernamePasswordAuthenticationToken authentication) {
        if (unknownUserPasswordHash == null) {
            unknownUserPasswordHash = passwordEncoder.encode(UNKNOWN_USER_PASSWORD);
        }
        if (authentication.getCredentials() != null) {
            passwordEncoder.matches(authentication.getCredentials().toString(), unknownUserPasswordHash);
        }
    }
}
