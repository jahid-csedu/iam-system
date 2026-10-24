package com.example.iamsystem.user.service;

import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.util.DateUtil.calculateExpiryDate;

/**
 * Builds a new, validated, active user inside a tenant. Used for root users at tenant signup and for every other user.
 */
@Component
@RequiredArgsConstructor
public class UserFactory {

    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final PasswordExpirationProperties passwordExpiration;

    public User newUser(UserCreationDto request, Tenant tenant) {
        userValidator.validateUsernameAvailable(request.username());
        userValidator.validateEmailAvailable(request.email());
        userValidator.validatePasswordPolicy(request.password());

        User user = userMapper.toEntity(request);
        user.setTenant(tenant);
        user.setActive(true);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordExpiryDate(calculateExpiryDate(passwordExpiration.days()));
        return user;
    }
}
