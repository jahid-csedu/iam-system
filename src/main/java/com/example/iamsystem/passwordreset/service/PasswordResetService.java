package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.session.service.SessionService;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserPasswordUpdater;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.example.iamsystem.passwordreset.PasswordResetConstants.INVALID_OTP;

/**
 * Self-service password reset for users who are not logged in: an OTP is emailed, and with a valid OTP the user sets a
 * new password of their choice. The OTP is only ever sent by email ({@link ResetOtpIssuer}).
 * <p>
 * The request's {@code client_id} decides the tenant, and the user is found by username or email inside that tenant only
 * (both are unique per tenant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    /**
     * Wrong guesses allowed against one OTP; the last one deletes it, and the user has to request a new one.
     */
    static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordResetOTPRepository otpRepository;
    private final ClientResolver clientResolver;
    private final ResetOtpIssuer otpIssuer;
    private final PasswordEncoder passwordEncoder;
    private final UserPasswordUpdater passwordUpdater;
    private final SessionService sessionService;

    /**
     * Emails the user an OTP ({@link ResetOtpIssuer}) after the request commits, so the answer never waits for the mail
     * server. An unknown user, a user without an email address and a user over the request limit are not errors: the
     * outcome is audited and logged, and the caller answers every request the same way and in about the same time, so
     * nobody can find out which accounts exist.
     */
    @Auditable(
            value = AuditEventType.PASSWORD_RESET_OTP_REQUEST,
            target = "#identifier",
            detailsExpression = "#result == null ? T(java.util.Map).of('client_id', #clientId)"
                    + " : T(java.util.Map).of('client_id', #clientId, 'request_outcome', #result)"
    )
    @Transactional
    public OtpRequestOutcome createPasswordResetOtp(String clientId, String identifier) {
        Long tenantId = clientResolver.resolve(clientId).getTenant().getId();
        Optional<User> user = findUser(identifier, tenantId);
        if (user.isEmpty()) {
            log.warn("Password reset requested for an unknown user in tenant {}", tenantId);
            otpIssuer.spendIssuingTime();
            return OtpRequestOutcome.UNKNOWN_USER;
        }
        return otpIssuer.issueAfterCommit(user.get());
    }

    /**
     * With a valid OTP the user's password becomes {@code newPassword} (password policy applies), any login lockout ends,
     * and every session of the user ends. The OTP is compared only with the pending OTP of the user named by the
     * identifier in the client's tenant. An unknown user, no pending OTP, an expired OTP and a wrong guess all get the same
     * error. Wrong guesses are counted (the transaction commits on {@link InvalidPasswordResetOTPException}, so the count
     * sticks), and the {@value #MAX_FAILED_ATTEMPTS}th one deletes the OTP. A new password that violates the policy rolls
     * everything back, so the OTP stays usable.
     */
    @Auditable(
            value = AuditEventType.PASSWORD_RESET,
            target = "#identifier",
            detailsExpression = "T(java.util.Map).of('client_id', #clientId)"
    )
    @Transactional(noRollbackFor = InvalidPasswordResetOTPException.class)
    public void resetPassword(String clientId, String identifier, String otp, String newPassword) {
        Long tenantId = clientResolver.resolve(clientId).getTenant().getId();
        PasswordResetOTP resetOtp = findUser(identifier, tenantId)
                .flatMap(otpRepository::findForUpdateByUser)
                .orElseThrow(() -> new InvalidPasswordResetOTPException(INVALID_OTP));
        if (resetOtp.isExpired()) {
            otpRepository.delete(resetOtp);
            throw new InvalidPasswordResetOTPException(INVALID_OTP);
        }
        if (!passwordEncoder.matches(otp, resetOtp.getOtpHash())) {
            recordFailedAttempt(resetOtp);
            throw new InvalidPasswordResetOTPException(INVALID_OTP);
        }
        User user = resetOtp.getUser();
        passwordUpdater.updatePassword(user, newPassword);
        sessionService.revokeAllOf(user.getId());
        otpRepository.delete(resetOtp);
    }

    /**
     * A username match wins over an email match, so a username that looks like someone's email is still unambiguous.
     */
    private Optional<User> findUser(String identifier, Long tenantId) {
        return userRepository.findByUsernameAndTenantId(identifier, tenantId)
                .or(() -> userRepository.findByEmailAndTenantId(identifier, tenantId));
    }

    private void recordFailedAttempt(PasswordResetOTP resetOtp) {
        resetOtp.setFailedAttempts(resetOtp.getFailedAttempts() + 1);
        Long userId = resetOtp.getUser().getId();
        if (resetOtp.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            otpRepository.delete(resetOtp);
            log.warn("Deleted the password-reset OTP of user {} after {} wrong guesses", userId, MAX_FAILED_ATTEMPTS);
        } else {
            log.warn("Wrong password-reset OTP for user {} ({} of {} attempts)", userId, resetOtp.getFailedAttempts(),
                    MAX_FAILED_ATTEMPTS);
        }
    }
}
