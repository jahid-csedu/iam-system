package com.example.iamsystem.security.user;

import com.example.iamsystem.role.Role;
import com.example.iamsystem.user.model.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultUserDetails implements UserDetails {

    private final User user;

    public DefaultUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return this.user;
    }

    public boolean isRootUser() {
        return user.isRootUser();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(Role::getPermissions)
                .flatMap(Set::stream)
                .map(permission -> new SimpleGrantedAuthority(permission.getServiceName() + ":" + permission.getAction()))
                .collect(Collectors.toSet());
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
        return !user.isUserLocked();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isUserLocked();
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
