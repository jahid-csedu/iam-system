package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.InvalidPasswordException;
import com.example.iamsystem.passwordreset.service.NewPasswordIssuer;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.session.service.SessionService;
import com.example.iamsystem.user.config.PasswordExpirationProperties;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_OLD_PASSWORD;
import static com.example.iamsystem.common.util.DateUtil.calculateExpiryDate;

/**
 * Changing your own password, and resetting another user's password.
 */
@Service
@RequiredArgsConstructor
public class UserPasswordService {

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final PasswordExpirationProperties passwordExpiration;
    private final NewPasswordIssuer newPasswordIssuer;
    private final SessionService sessionService;

    /**
     * Only for yourself, and always with the old password. Logs you out everywhere except the session making the change.
     */
    @Auditable(AuditEventType.PASSWORD_CHANGE)
    @Transactional
    public void changeOwnPassword(PasswordChangeDto request) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User user = userRepository.getRequiredByIdAndTenantId(actor.userId(), actor.homeTenantId());
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new InvalidPasswordException(INVALID_OLD_PASSWORD);
        }
        userValidator.validatePasswordPolicy(request.newPassword());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordExpiryDate(calculateExpiryDate(passwordExpiration.days()));
        userRepository.save(user);
        sessionService.revokeOthersOf(user.getId(), actor.sessionId());
    }

    /**
     * Gives another user of the tenant a new random password, sent only to that user's email address.
     */
    @Auditable(
            value = AuditEventType.PASSWORD_RESET_BY_USER,
            target = "#userId"
    )
    @Transactional
    public void resetPassword(Long userId) {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        User target = userRepository.getRequiredByIdAndTenantId(userId, actor.tenantId());
        accessPolicy.checkCanResetPassword(actor, target);
        newPasswordIssuer.issueNewPassword(target);
    }
}
