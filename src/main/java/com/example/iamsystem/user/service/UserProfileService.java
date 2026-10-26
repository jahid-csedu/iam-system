package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.InvalidPasswordException;
import com.example.iamsystem.passwordreset.service.ResetOtpIssuer;
import com.example.iamsystem.security.service.FailedPasswordCounter;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.OwnProfileUpdateDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.dto.UserUpdateDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_CURRENT_PASSWORD;
import static com.example.iamsystem.permission.PermissionKey.USERS_RESET_PASSWORD;

/**
 * Changes a user's profile (full name, email): another user's inside the current tenant, or a management user's own.
 * <p>
 * Whoever controls a user's email controls their password resets. So a new email address needs
 * {@code users:reset-password} on top of {@code users:update} for another user, and your current password for yourself;
 * and any pending reset OTP (sent to the old address) is deleted. A root's email must be unused by every user of every
 * tenant, anyone else's by the users of their tenant and by every root.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserAccessPolicy accessPolicy;
    private final UserValidator userValidator;
    private final AuthorizationService authorizationService;
    private final ResetOtpIssuer resetOtpIssuer;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final FailedPasswordCounter failedPasswordCounter;
    private final UserMapper userMapper;

    @Auditable(
            value = AuditEventType.USER_UPDATE,
            target = "#id",
            detailsExpression = "T(java.util.Map).of('fields', #request.fields())"
    )
    @Transactional
    public UserDto updateUser(Long id, UserUpdateDto request) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = userRepository.getRequiredByIdAndTenantId(id, actor.tenantId());
        accessPolicy.checkCanUpdateProfile(actor, user);
        if (isNewEmail(user, request.email())) {
            authorizationService.checkPermission(actor, USERS_RESET_PASSWORD);
            userValidator.validateEmailAvailable(request.email(), actor.tenantId());
        }
        return userMapper.toDto(apply(user, request));
    }

    /**
     * Your own profile, as a management user (the root's only way to change theirs), always in your home tenant. A wrong
     * current password counts like a failed login (a missing one is no guess).
     */
    @Auditable(
            value = AuditEventType.USER_UPDATE,
            detailsExpression = "T(java.util.Map).of('fields', #request.changes().fields(), 'own_profile', true)"
    )
    @Transactional
    public void updateOwnProfile(OwnProfileUpdateDto request) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = userRepository.getRequiredByIdAndTenantId(actor.userId(), actor.homeTenantId());
        accessPolicy.checkIsManagementUser(user);
        if (isNewEmail(user, request.email())) {
            checkCurrentPassword(user, request.currentPassword());
            if (user.isRootUser()) {
                userValidator.validateEmailAvailableForRoot(request.email());
            } else {
                userValidator.validateEmailAvailable(request.email(), actor.homeTenantId());
            }
        }
        apply(user, request.changes());
    }

    private void checkCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null) {
            throw new InvalidPasswordException(INVALID_CURRENT_PASSWORD);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            failedPasswordCounter.recordFailure(user.getId());
            throw new InvalidPasswordException(INVALID_CURRENT_PASSWORD);
        }
        failedPasswordCounter.recordSuccess(user);
    }

    /**
     * A different address, not just the same one in other letter case.
     */
    private static boolean isNewEmail(User user, String email) {
        return email != null && !email.equalsIgnoreCase(user.getEmail());
    }

    private User apply(User user, UserUpdateDto changes) {
        if (isNewEmail(user, changes.email())) {
            resetOtpIssuer.discardPending(user);
        }
        if (changes.email() != null) {
            user.setEmail(changes.email());
        }
        if (changes.fullName() != null) {
            user.setFullName(changes.fullName().isBlank() ? null : changes.fullName());
        }
        return userRepository.save(user);
    }
}
