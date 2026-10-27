package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.email.dto.EmailPurpose;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.passwordreset.config.OtpExpirationProperties;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetOtpIssuerTest {

    @Mock
    private PasswordResetOTPRepository otpRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private ResetRequestLimiter requestLimiter;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private ResetOtpIssuer issuer;
    private User user;

    @BeforeEach
    void setUp() {
        issuer = new ResetOtpIssuer(otpRepository, emailService, new OtpExpirationProperties(5), requestLimiter, passwordEncoder);
        lenient().when(requestLimiter.tryRecord(any(User.class))).thenReturn(true);
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
    }

    @Test
    @DisplayName("A six-digit OTP valid for the configured minutes is saved only as a hash and emailed to the user")
    void issue_savesTheHashAndEmailsTheOtp() {
        assertEquals(OtpRequestOutcome.SENT, issuer.issue(user));

        ArgumentCaptor<PasswordResetOTP> saved = ArgumentCaptor.forClass(PasswordResetOTP.class);
        verify(otpRepository).save(saved.capture());
        String hash = saved.getValue().getOtpHash();
        assertTrue(saved.getValue().getExpiryDate().isBefore(Instant.now().plus(Duration.ofMinutes(6))));

        ArgumentCaptor<OutgoingEmail> mail = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailService).send(mail.capture());
        assertEquals(EmailPurpose.PASSWORD_RESET_OTP, mail.getValue().purpose());
        assertEquals("alice@example.com", mail.getValue().to());
        assertEquals(5, mail.getValue().values().get("validMinutes"));
        String otp = (String) mail.getValue().values().get("otp");
        assertTrue(otp.matches("\\d{6}"));
        assertNotEquals(otp, hash);
        assertTrue(passwordEncoder.matches(otp, hash));
    }

    @Test
    @DisplayName("A user without an email address gets nothing, and nothing counts towards the limit")
    void issue_userWithoutEmail_sendsNothing() {
        user.setEmail(null);

        assertEquals(OtpRequestOutcome.NO_EMAIL, issuer.issue(user));
        verifyNoInteractions(otpRepository, emailService, requestLimiter);
    }

    @Test
    @DisplayName("Over the request limit, no OTP is made and nothing is sent")
    void issue_overTheLimit_sendsNothing() {
        when(requestLimiter.tryRecord(user)).thenReturn(false);

        assertEquals(OtpRequestOutcome.TOO_MANY_REQUESTS, issuer.issue(user));
        verifyNoInteractions(otpRepository, emailService);
    }

    @Test
    @DisplayName("A failed email is no error: the outcome says so")
    void issue_mailFailure_isReported() {
        doThrow(new MailSendException("SMTP down")).when(emailService).send(any(OutgoingEmail.class));

        assertEquals(OtpRequestOutcome.MAIL_FAILED, issuer.issue(user));
    }

    @Test
    @DisplayName("Discarding deletes the user's pending OTP")
    void discardPending_deletesTheUsersOtp() {
        issuer.discardPending(user);

        verify(otpRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("A new OTP replaces the user's existing one")
    void issue_replacesTheExistingOtp() {
        PasswordResetOTP existing = new PasswordResetOTP();
        when(otpRepository.findByUser(user)).thenReturn(Optional.of(existing));

        issuer.issue(user);

        verify(otpRepository).delete(existing);
        verify(otpRepository).save(any(PasswordResetOTP.class));
    }
}
