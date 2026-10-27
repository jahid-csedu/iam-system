package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.email.dto.EmailPurpose;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.otp.entity.OtpPurpose;
import com.example.iamsystem.otp.service.OneTimeCodes;
import com.example.iamsystem.otp.service.OtpRequestLimiter;
import com.example.iamsystem.passwordreset.config.OtpExpirationProperties;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The one place that makes (and discards) password-reset OTPs, for the user's own forgot-password request and for a reset by another
 * user: a random six-digit OTP ({@link OneTimeCodes}), stored only as a bcrypt hash, replacing any
 * earlier one, and emailed to the user. Every OTP counts towards the user's hourly limit ({@link OtpRequestLimiter}).
 * <p>
 * Nothing here is an error; the outcome says what happened and the caller decides what the client sees. Runs inside the
 * caller's transaction, which must not have changed the user's row yet (the limit locks it in a transaction of its own).
 * <p>
 * The bcrypt hash is the slow part of a request (tens of milliseconds), so it is made first on every path, and
 * {@link #spendIssuingTime()} makes one for a request about nobody: the response time doesn't tell whether an account
 * exists, has an email address or is over the limit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResetOtpIssuer {

    private static final String OTP_VALUE = "otp";
    private static final String VALID_MINUTES_VALUE = "validMinutes";

    private final PasswordResetOTPRepository otpRepository;
    private final EmailService emailService;
    private final OtpExpirationProperties otpExpiration;
    private final OtpRequestLimiter requestLimiter;
    private final OneTimeCodes oneTimeCodes;
    private final PasswordEncoder passwordEncoder;

    /**
     * For the user's own request: only to a verified address (whoever asks needn't own the account, so an unverified
     * address could be someone else's), and the email goes out after the caller's transaction commits, so the request
     * doesn't wait for it.
     *
     * @return {@code QUEUED}, {@code NO_EMAIL}, {@code EMAIL_NOT_VERIFIED} or {@code TOO_MANY_REQUESTS}; a later mail
     *         failure is only logged, and the OTP stays unused until it expires or the next one replaces it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OtpRequestOutcome issueAfterCommit(User user) {
        return issue(user, Delivery.AFTER_COMMIT);
    }

    /**
     * For a reset by another user, who vouches for the address (so it may be unverified) and changes the account only
     * once the email is out: it is sent at once.
     *
     * @return {@code SENT}, {@code NO_EMAIL}, {@code TOO_MANY_REQUESTS} or {@code MAIL_FAILED}; an OTP whose email failed
     *         stays unused until it expires or the next one replaces it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OtpRequestOutcome issueNow(User user) {
        return issue(user, Delivery.NOW);
    }

    /**
     * Spends the time of making an OTP without making one, for a request that names no user.
     */
    public void spendIssuingTime() {
        passwordEncoder.encode(oneTimeCodes.newCode());
    }

    private OtpRequestOutcome issue(User user, Delivery delivery) {
        String otp = oneTimeCodes.newCode();
        String otpHash = passwordEncoder.encode(otp);
        if (user.getEmail() == null) {
            log.warn("No password-reset OTP for user {}, who has no email address", user.getId());
            return OtpRequestOutcome.NO_EMAIL;
        }
        if (delivery == Delivery.AFTER_COMMIT && !user.isEmailVerified()) {
            log.warn("No password-reset OTP for user {}, whose email address isn't verified", user.getId());
            return OtpRequestOutcome.EMAIL_NOT_VERIFIED;
        }
        if (!requestLimiter.tryRecord(user, OtpPurpose.PASSWORD_RESET)) {
            log.warn("No password-reset OTP for user {}: too many in the last hour", user.getId());
            return OtpRequestOutcome.TOO_MANY_REQUESTS;
        }

        otpRepository.findByUser(user).ifPresent(existingOtp -> {
            otpRepository.delete(existingOtp);
            otpRepository.flush();
        });
        PasswordResetOTP resetOtp = new PasswordResetOTP();
        resetOtp.setUser(user);
        resetOtp.setOtpHash(otpHash);
        resetOtp.setExpiryDate(Instant.now().plus(Duration.ofMinutes(otpExpiration.minutes())));
        otpRepository.save(resetOtp);

        OutgoingEmail email = new OutgoingEmail(EmailPurpose.PASSWORD_RESET_OTP, user.getEmail(),
                Map.of(OTP_VALUE, otp, VALID_MINUTES_VALUE, otpExpiration.minutes()));
        if (delivery == Delivery.NOW) {
            return sendNow(email);
        }
        emailService.sendAfterCommit(email);
        return OtpRequestOutcome.QUEUED;
    }

    private OtpRequestOutcome sendNow(OutgoingEmail email) {
        try {
            emailService.send(email);
            return OtpRequestOutcome.SENT;
        } catch (MailException e) {
            log.error("Could not email a password-reset OTP", e);
            return OtpRequestOutcome.MAIL_FAILED;
        }
    }

    /**
     * Deletes the user's pending OTP, if any; for when it went to an email address the user no longer has.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardPending(User user) {
        otpRepository.deleteByUser(user);
    }

    private enum Delivery {
        /**
         * At once, inside the request (a reset by another user).
         */
        NOW,
        /**
         * After the request commits (the user's own request), and only to a verified address.
         */
        AFTER_COMMIT
    }
}
