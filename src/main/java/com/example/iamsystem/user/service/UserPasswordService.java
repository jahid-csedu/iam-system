package com.example.iamsystem.user.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.EmailNotSentException;
import com.example.iamsystem.common.exception.InvalidPasswordException;
import com.example.iamsystem.common.exception.TooManyRequestsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.passwordreset.service.ResetOtpIssuer;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.session.service.SessionService;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_OLD_PASSWORD;
import static com.example.iamsystem.common.exception.ErrorMessage.RESET_EMAIL_NOT_SENT;
import static com.example.iamsystem.common.exception.ErrorMessage.TOO_MANY_RESET_OTPS;
import static com.example.iamsystem.common.exception.ErrorMessage.USER_HAS_NO_EMAIL;

/**
 * Changing your own password, and resetting another user's password.
 */
@Service
@RequiredArgsConstructor
public class UserPasswordService {

    private final UserRepository userRepository;
    private final UserAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserPasswordUpdater passwordUpdater;
    private final ResetOtpIssuer otpIssuer;
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
        passwordUpdater.updatePassword(user, request.newPassword());
        sessionService.revokeOthersOf(user.getId(), actor.sessionId());
    }

    /**
     * For another user of the tenant, typically when their account may be compromised: emails them a reset OTP (counted
     * like their own requests), and once it is sent their current password stops working and all their sessions end, so
     * only the OTP gets them back in, with a password they choose. Nothing changes when the OTP can't be sent. The OTP is
     * issued before the user's row is changed, because the request limit locks that row in a transaction of its own.
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
        switch (otpIssuer.issue(target)) {
            case SENT -> {
                passwordUpdater.disablePassword(target);
                sessionService.revokeAllOf(target.getId());
            }
            case NO_EMAIL -> throw new UserInputNotValidException(USER_HAS_NO_EMAIL);
            case TOO_MANY_REQUESTS -> throw new TooManyRequestsException(TOO_MANY_RESET_OTPS);
            case MAIL_FAILED -> throw new EmailNotSentException(RESET_EMAIL_NOT_SENT);
            case UNKNOWN_USER -> throw new IllegalStateException("The OTP issuer never reports an unknown user");
        }
    }
}
