package com.example.iamsystem.user.service;

import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.util.DateUtil.calculateExpiryDate;

/**
 * Builds new, validated, active users: the root user of a new tenant, and every other user inside a tenant.
 */
@Component
@RequiredArgsConstructor
public class UserFactory {

    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final PasswordExpirationProperties passwordExpiration;

    public User newUser(UserCreationDto request, Tenant tenant, UserType userType) {
        userValidator.validateAvailable(request.username(), request.email(), tenant.getId());
        return build(request, tenant, userType);
    }

    /**
     * The root user's username and email must be unused across all tenants. The root is a management user.
     */
    public User newRootUser(UserCreationDto request, Tenant tenant) {
        userValidator.validateAvailableForRoot(request.username(), request.email());
        User root = build(request, tenant, UserType.MANAGEMENT);
        root.setRootUser(true);
        return root;
    }

    private User build(UserCreationDto request, Tenant tenant, UserType userType) {
        userValidator.validatePasswordPolicy(request.password());

        User user = userMapper.toEntity(request);
        user.setTenant(tenant);
        user.setUserType(userType);
        user.setActive(true);
        user.setEmailVerified(false);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordExpiryDate(calculateExpiryDate(passwordExpiration.days()));
        return user;
    }
}
