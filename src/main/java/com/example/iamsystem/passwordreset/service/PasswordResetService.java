package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.passwordreset.config.OtpExpirationProperties;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.example.iamsystem.passwordreset.PasswordResetConstants.INVALID_OTP;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_REQUEST_BODY_PREFIX;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_REQUEST_SUBJECT;

/**
 * Self-service password reset for users who are not logged in: an OTP is emailed, and a valid OTP gets the user a new
 * password by email. The OTP and the password are only ever sent by email.
 * <p>
 * The request's {@code client_id} decides the tenant, and the user is found by username or email inside that tenant only
 * (both are unique per tenant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int OTP_BOUND = 1_000_000;
    private static final String OTP_FORMAT = "%06d";
    /**
     * Wrong guesses allowed against one OTP; the last one deletes it, and the user has to request a new one.
     */
    static final int MAX_FAILED_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordResetOTPRepository otpRepository;
    private final JavaMailSender mailSender;
    private final NewPasswordIssuer newPasswordIssuer;
    private final ClientResolver clientResolver;
    private final OtpExpirationProperties otpExpiration;
    private final ResetRequestLimiter requestLimiter;

    /**
     * Replaces any earlier OTP of the user. An unknown user, a user without an email address, a user over the request limit
     * ({@link ResetRequestLimiter}) and a failed email are not errors: the outcome is audited and logged, and the caller answers every request the same way, so nobody can find
     * out which accounts exist. An OTP whose email failed stays unused until it expires or the next request replaces it.
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
        Optional<User> found = findUser(identifier, tenantId);
        if (found.isEmpty()) {
            log.warn("Password reset requested for an unknown user in tenant {}", tenantId);
            return OtpRequestOutcome.UNKNOWN_USER;
        }
        User user = found.get();
        if (user.getEmail() == null) {
            log.warn("Password reset requested for user {}, who has no email address", user.getId());
            return OtpRequestOutcome.NO_EMAIL;
        }
        if (!requestLimiter.tryRecord(user)) {
            log.warn("Password reset for user {} refused: too many requests in the last hour", user.getId());
            return OtpRequestOutcome.TOO_MANY_REQUESTS;
        }

        otpRepository.findByUser(user).ifPresent(existingOtp -> {
            otpRepository.delete(existingOtp);
            otpRepository.flush();
        });

        String otp = String.format(OTP_FORMAT, random.nextInt(OTP_BOUND));
        PasswordResetOTP resetOtp = new PasswordResetOTP();
        resetOtp.setUser(user);
        resetOtp.setOtp(otp);
        resetOtp.setExpiryDate(Instant.now().plus(Duration.ofMinutes(otpExpiration.minutes())));
        otpRepository.save(resetOtp);

        SimpleMailMessage emailMessage = new SimpleMailMessage();
        emailMessage.setTo(user.getEmail());
        emailMessage.setSubject(PASSWORD_RESET_REQUEST_SUBJECT);
        emailMessage.setText(PASSWORD_RESET_REQUEST_BODY_PREFIX + otp);
        try {
            mailSender.send(emailMessage);
            return OtpRequestOutcome.SENT;
        } catch (MailException e) {
            log.error("Could not email the password-reset OTP to user {}", user.getId(), e);
            return OtpRequestOutcome.MAIL_FAILED;
        }
    }

    @Auditable(
            value = AuditEventType.PASSWORD_RESET,
            target = "#identifier",
            detailsExpression = "T(java.util.Map).of('client_id', #clientId)"
    )
    /**
     * The OTP is compared only with the pending OTP of the user named by the identifier in the client's tenant. An unknown
     * user, no pending OTP, an expired OTP and a wrong guess all get the same error. Wrong guesses are counted (the
     * transaction commits on {@link InvalidPasswordResetOTPException}, so the count sticks), and the
     * {@value #MAX_FAILED_ATTEMPTS}th one deletes the OTP.
     */
    @Transactional(noRollbackFor = InvalidPasswordResetOTPException.class)
    public void resetPassword(String clientId, String identifier, String otp) {
        Long tenantId = clientResolver.resolve(clientId).getTenant().getId();
        PasswordResetOTP resetOtp = findUser(identifier, tenantId)
                .flatMap(otpRepository::findForUpdateByUser)
                .orElseThrow(() -> new InvalidPasswordResetOTPException(INVALID_OTP));
        if (resetOtp.isExpired()) {
            otpRepository.delete(resetOtp);
            throw new InvalidPasswordResetOTPException(INVALID_OTP);
        }
        if (!matches(resetOtp, otp)) {
            recordFailedAttempt(resetOtp);
            throw new InvalidPasswordResetOTPException(INVALID_OTP);
        }
        newPasswordIssuer.issueNewPassword(resetOtp.getUser());
        otpRepository.delete(resetOtp);
    }

    /**
     * A username match wins over an email match, so a username that looks like someone's email is still unambiguous.
     */
    private Optional<User> findUser(String identifier, Long tenantId) {
        return userRepository.findByUsernameAndTenantId(identifier, tenantId)
                .or(() -> userRepository.findByEmailAndTenantId(identifier, tenantId));
    }

    /**
     * Compares in constant time, so the response time doesn't tell how many leading digits were right.
     */
    private static boolean matches(PasswordResetOTP resetOtp, String otp) {
        return MessageDigest.isEqual(resetOtp.getOtp().getBytes(StandardCharsets.UTF_8), otp.getBytes(StandardCharsets.UTF_8));
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
