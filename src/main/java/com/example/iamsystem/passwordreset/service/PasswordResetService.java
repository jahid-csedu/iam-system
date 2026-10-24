package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.client.service.ClientResolver;
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
 * The request's {@code client_id} decides the tenant, and the user is found by username or email inside that tenant only
 * (both are unique per tenant).
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
    private final ClientResolver clientResolver;
    private final OtpExpirationProperties otpExpiration;

    /**
     * Replaces any earlier OTP of the user.
     */
    @Auditable(
            value = AuditEventType.PASSWORD_RESET_OTP_REQUEST,
            target = "#identifier",
            detailsExpression = "T(java.util.Map).of('client_id', #clientId)"
    )
    @Transactional
    public void createPasswordResetOtp(String clientId, String identifier) {
        Long tenantId = clientResolver.resolve(clientId).getTenant().getId();
        User user = findUser(identifier, tenantId).orElseThrow(() -> new DataNotFoundException(USER_NOT_FOUND));

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
            target = "#identifier",
            detailsExpression = "T(java.util.Map).of('client_id', #clientId)"
    )
    @Transactional
    public void resetPassword(String clientId, String identifier, String otp) {
        Long tenantId = clientResolver.resolve(clientId).getTenant().getId();
        PasswordResetOTP resetOtp = otpRepository.findByOtp(otp);
        validateOtp(identifier, tenantId, resetOtp);
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
     * The OTP must belong to the user named by the identifier, inside the client's tenant.
     */
    private static void validateOtp(String identifier, Long tenantId, PasswordResetOTP resetOtp) {
        if (resetOtp == null) {
            throw new InvalidPasswordResetOTPException(INVALID_OTP);
        }
        User owner = resetOtp.getUser();
        boolean identifierMatches = identifier.equals(owner.getUsername()) || identifier.equals(owner.getEmail());
        if (!identifierMatches || !tenantId.equals(owner.getTenant().getId())) {
            throw new InvalidPasswordResetOTPException(OTP_DOES_NOT_BELONG_TO_USER);
        }
        if (resetOtp.getExpiryDate().isBefore(Instant.now())) {
            throw new InvalidPasswordResetOTPException(OTP_HAS_EXPIRED);
        }
    }
}
