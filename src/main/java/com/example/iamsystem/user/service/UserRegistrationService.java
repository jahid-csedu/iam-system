package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.dto.UserRegistrationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.NO_PERMISSION;
import static com.example.iamsystem.common.util.DateUtil.calculateExpiryDate;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final PasswordExpirationProperties passwordExpiration;

    /**
     * A root user registers themselves without being logged in. Every other user is created by a logged-in
     * user with the user-creation permission, and becomes part of that user's hierarchy.
     */
    @Auditable(
            value = AuditEventType.USER_REGISTRATION,
            target = "#request.username",
            detailsExpression = "T(java.util.Map).of('new_user_id', #result.id, 'new_username', #result.username)"
    )
    @Transactional
    public UserDto registerUser(UserRegistrationDto request) {
        userValidator.validateUsernameAvailable(request.getUsername());
        userValidator.validateEmailAvailable(request.getEmail());
        userValidator.validatePasswordPolicy(request.getPassword());

        User user = userMapper.toEntity(request);
        if (request.isRootUser()) {
            ensureNobodyIsLoggedIn();
        } else {
            user.setCreatedBy(requireCreator());
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordExpiryDate(calculateExpiryDate(passwordExpiration.days()));
        return userMapper.toDto(userRepository.save(user));
    }

    private void ensureNobodyIsLoggedIn() {
        if (currentUserProvider.currentUser().isPresent()) {
            throw new NoAccessException(NO_PERMISSION);
        }
    }

    private User requireCreator() {
        User creator = currentUserProvider.currentUser()
                .orElseThrow(() -> new NoAccessException(NO_PERMISSION));
        accessPolicy.checkCanCreateUsers(creator);
        return creator;
    }
}
