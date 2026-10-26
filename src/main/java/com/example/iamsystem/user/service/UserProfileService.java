package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.passwordreset.service.ResetOtpIssuer;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.dto.UserUpdateDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.mapper.UserMapper;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.permission.PermissionKey.USERS_RESET_PASSWORD;

/**
 * Changes another user's profile (full name, email) inside the current tenant; your own goes through {@code /api/me}.
 * <p>
 * Whoever controls a user's email controls their password resets, so a new email address also needs
 * {@code users:reset-password} on top of the endpoint's {@code users:update}, and any pending reset OTP (sent to the old
 * address) is deleted.
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

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            authorizationService.checkPermission(actor, USERS_RESET_PASSWORD);
            userValidator.validateEmailAvailable(request.email(), actor.tenantId());
            resetOtpIssuer.discardPending(user);
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName().isBlank() ? null : request.fullName());
        }
        return userMapper.toDto(userRepository.save(user));
    }
}
