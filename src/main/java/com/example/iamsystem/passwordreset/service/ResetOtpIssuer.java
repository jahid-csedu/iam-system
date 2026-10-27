package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.email.dto.EmailPurpose;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.service.EmailService;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The one place that makes (and discards) password-reset OTPs, for the user's own forgot-password request and for a reset by another
 * user: a random six-digit OTP, stored only as a bcrypt hash (six digits are too few for a fast hash), replacing any
 * earlier one, and emailed to the user. Every OTP counts towards the user's hourly limit ({@link ResetRequestLimiter}).
 * <p>
 * Nothing here is an error; the outcome says what happened and the caller decides what the client sees. Runs inside the
 * caller's transaction, which must not have changed the user's row yet (the limit locks it in a transaction of its own).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResetOtpIssuer {

    private static final int OTP_BOUND = 1_000_000;
    private static final String OTP_FORMAT = "%06d";
    private static final String OTP_VALUE = "otp";
    private static final String VALID_MINUTES_VALUE = "validMinutes";

    private final SecureRandom random = new SecureRandom();
    private final PasswordResetOTPRepository otpRepository;
    private final EmailService emailService;
    private final OtpExpirationProperties otpExpiration;
    private final ResetRequestLimiter requestLimiter;
    private final PasswordEncoder passwordEncoder;

    /**
     * @return {@code SENT}, {@code NO_EMAIL}, {@code TOO_MANY_REQUESTS} or {@code MAIL_FAILED}; an OTP whose email failed
     *         stays unused until it expires or the next one replaces it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OtpRequestOutcome issue(User user) {
        if (user.getEmail() == null) {
            log.warn("No password-reset OTP for user {}, who has no email address", user.getId());
            return OtpRequestOutcome.NO_EMAIL;
        }
        if (!requestLimiter.tryRecord(user)) {
            log.warn("No password-reset OTP for user {}: too many in the last hour", user.getId());
            return OtpRequestOutcome.TOO_MANY_REQUESTS;
        }

        otpRepository.findByUser(user).ifPresent(existingOtp -> {
            otpRepository.delete(existingOtp);
            otpRepository.flush();
        });
        String otp = String.format(OTP_FORMAT, random.nextInt(OTP_BOUND));
        PasswordResetOTP resetOtp = new PasswordResetOTP();
        resetOtp.setUser(user);
        resetOtp.setOtpHash(passwordEncoder.encode(otp));
        resetOtp.setExpiryDate(Instant.now().plus(Duration.ofMinutes(otpExpiration.minutes())));
        otpRepository.save(resetOtp);

        try {
            emailService.send(new OutgoingEmail(EmailPurpose.PASSWORD_RESET_OTP, user.getEmail(),
                    Map.of(OTP_VALUE, otp, VALID_MINUTES_VALUE, otpExpiration.minutes())));
            return OtpRequestOutcome.SENT;
        } catch (MailException e) {
            log.error("Could not email the password-reset OTP to user {}", user.getId(), e);
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
}
