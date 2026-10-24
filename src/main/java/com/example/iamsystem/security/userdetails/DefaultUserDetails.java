package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security's view of a user during login and token refresh (password and account checks).
 * Authenticated requests carry an {@link AuthenticatedActor} instead; permissions are not Spring authorities.
 */
public record DefaultUserDetails(User user) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return !user.isPasswordExpired()
                && (user.getPasswordExpiryDate() == null || user.getPasswordExpiryDate().isAfter(Instant.now()));
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isUserLocked()
                || (user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isBefore(Instant.now()));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !user.isPasswordExpired();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
