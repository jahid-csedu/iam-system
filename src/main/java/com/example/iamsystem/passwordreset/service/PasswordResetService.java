package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.passwordreset.config.OtpExpirationProperties;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.example.iamsystem.passwordreset.PasswordResetConstants.INVALID_OTP;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.OTP_DOES_NOT_BELONG_TO_USER;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.OTP_HAS_EXPIRED;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_REQUEST_BODY_PREFIX;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_REQUEST_SUBJECT;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.USER_NOT_FOUND;

/**
 * Self-service password reset for users who are not logged in: an OTP is emailed, and a valid OTP gets the user a new
 * password by email. The OTP and the password are only ever sent by email.
 * <p>
 * Users are found by username or email across all tenants, which is unambiguous only while both are globally unique.
 * Step 8 (per-tenant uniqueness) must look the user up inside the tenant of the request's {@code client_id}.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int OTP_BOUND = 1_000_000;
    private static final String OTP_FORMAT = "%06d";

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordResetOTPRepository otpRepository;
    private final JavaMailSender mailSender;
    private final NewPasswordIssuer newPasswordIssuer;
    private final OtpExpirationProperties otpExpiration;

    /**
     * Replaces any earlier OTP of the user.
     */
    @Auditable(
            value = AuditEventType.PASSWORD_RESET_OTP_REQUEST,
            target = "#identifier"
    )
    @Transactional
    public void createPasswordResetOtp(String identifier) {
        User user = findUser(identifier).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));

        PasswordResetOTP existingOtp = otpRepository.findByUser(user);
        if (existingOtp != null) {
            otpRepository.delete(existingOtp);
            otpRepository.flush();
        }

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
        mailSender.send(emailMessage);
    }

    @Auditable(
            value = AuditEventType.PASSWORD_RESET,
            target = "#identifier"
    )
    @Transactional
    public void resetPassword(String identifier, String otp) {
        PasswordResetOTP resetOtp = otpRepository.findByOtp(otp);
        validateOtp(identifier, resetOtp);
        newPasswordIssuer.issueNewPassword(resetOtp.getUser());
        otpRepository.delete(resetOtp);
    }

    /**
     * A username match wins over an email match, so a username that looks like someone's email is still unambiguous.
     */
    private Optional<User> findUser(String identifier) {
        return userRepository.findByUsername(identifier).or(() -> userRepository.findByEmail(identifier));
    }

    private static void validateOtp(String identifier, PasswordResetOTP resetOtp) {
        if (resetOtp == null) {
            throw new InvalidPasswordResetOTPException(INVALID_OTP);
        }
        User owner = resetOtp.getUser();
        if (!identifier.equals(owner.getUsername()) && !identifier.equals(owner.getEmail())) {
            throw new InvalidPasswordResetOTPException(OTP_DOES_NOT_BELONG_TO_USER);
        }
        if (resetOtp.getExpiryDate().isBefore(Instant.now())) {
            throw new InvalidPasswordResetOTPException(OTP_HAS_EXPIRED);
        }
    }
}
