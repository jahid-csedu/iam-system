package com.example.iamsystem.emailverification.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.InvalidOtpException;
import com.example.iamsystem.common.exception.TooManyRequestsException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.email.dto.EmailPurpose;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.emailverification.config.EmailVerificationProperties;
import com.example.iamsystem.emailverification.entity.EmailVerificationCode;
import com.example.iamsystem.emailverification.repository.EmailVerificationCodeRepository;
import com.example.iamsystem.otp.entity.OtpPurpose;
import com.example.iamsystem.otp.service.OneTimeCodes;
import com.example.iamsystem.otp.service.OtpRequestLimiter;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.example.iamsystem.common.exception.ErrorMessage.EMAIL_ALREADY_VERIFIED;
import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_VERIFICATION_CODE;
import static com.example.iamsystem.common.exception.ErrorMessage.NO_EMAIL_TO_VERIFY;
import static com.example.iamsystem.common.exception.ErrorMessage.TOO_MANY_VERIFICATION_CODES;
import static com.example.iamsystem.otp.service.OneTimeCodes.MAX_FAILED_ATTEMPTS;

/**
 * Verifies the logged-in user's own email address: a code is emailed to it, and typing the code back proves the user
 * receives mail there. Codes are like reset OTPs ({@link OneTimeCodes}): a bcrypt hash only, replacing any earlier one,
 * {@value OneTimeCodes#MAX_FAILED_ATTEMPTS} wrong guesses, at most three per hour ({@link OtpRequestLimiter}), valid for
 * {@code iam.email-verification.code-minutes}. Always the user's own account in their home tenant, also for a platform
 * user working inside another tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final String CODE_VALUE = "code";
    private static final String VALID_MINUTES_VALUE = "validMinutes";

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final OneTimeCodes oneTimeCodes;
    private final OtpRequestLimiter requestLimiter;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailVerificationProperties properties;

    /**
     * Emails a new code to your address once the request commits. Refused when there's nothing to verify (no address, or
     * already verified) and over the hourly limit.
     */
    @Auditable(AuditEventType.EMAIL_VERIFICATION_REQUEST)
    @Transactional
    public void requestCode() {
        User user = currentUser();
        if (user.getEmail() == null) {
            throw new UserInputNotValidException(NO_EMAIL_TO_VERIFY);
        }
        if (user.isEmailVerified()) {
            throw new UserInputNotValidException(EMAIL_ALREADY_VERIFIED);
        }
        if (!requestLimiter.tryRecord(user, OtpPurpose.EMAIL_VERIFICATION)) {
            throw new TooManyRequestsException(TOO_MANY_VERIFICATION_CODES);
        }
        String code = oneTimeCodes.newCode();
        codeRepository.deleteByUser(user);
        EmailVerificationCode pending = new EmailVerificationCode();
        pending.setUser(user);
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setExpiresAt(Instant.now().plus(Duration.ofMinutes(properties.codeMinutes())));
        codeRepository.save(pending);
        emailService.sendAfterCommit(new OutgoingEmail(EmailPurpose.EMAIL_VERIFICATION, user.getEmail(),
                Map.of(CODE_VALUE, code, VALID_MINUTES_VALUE, properties.codeMinutes())));
    }

    /**
     * With the right code your address is verified. No pending code, an expired code and a wrong guess all get the same
     * error; wrong guesses are counted (the transaction commits on {@link InvalidOtpException}, so the count sticks), and
     * the last allowed one deletes the code.
     */
    @Auditable(AuditEventType.EMAIL_VERIFICATION)
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void confirm(String code) {
        User user = currentUser();
        EmailVerificationCode pending = codeRepository.findForUpdateByUser(user)
                .orElseThrow(() -> new InvalidOtpException(INVALID_VERIFICATION_CODE));
        if (pending.isExpired()) {
            codeRepository.delete(pending);
            throw new InvalidOtpException(INVALID_VERIFICATION_CODE);
        }
        if (!passwordEncoder.matches(code, pending.getCodeHash())) {
            recordFailedAttempt(pending);
            throw new InvalidOtpException(INVALID_VERIFICATION_CODE);
        }
        user.setEmailVerified(true);
        codeRepository.delete(pending);
        log.info("User {} verified their email address", user.getId());
    }

    /**
     * Deletes the user's pending code, if any; for when it went to an email address the user no longer has.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardPending(User user) {
        codeRepository.deleteByUser(user);
    }

    private User currentUser() {
        AuthenticatedActor actor = currentUserProvider.requireCurrentActor();
        return userRepository.getRequiredByIdAndTenantId(actor.userId(), actor.homeTenantId());
    }

    private void recordFailedAttempt(EmailVerificationCode pending) {
        pending.setFailedAttempts(pending.getFailedAttempts() + 1);
        if (pending.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            codeRepository.delete(pending);
            log.warn("Deleted the email verification code of user {} after {} wrong guesses", pending.getUser().getId(),
                    MAX_FAILED_ATTEMPTS);
        }
    }
}
