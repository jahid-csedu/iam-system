package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.passwordreset.config.OtpExpirationProperties;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetOTPRepository otpRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private NewPasswordIssuer newPasswordIssuer;

    private PasswordResetService service;
    private User user;
    private PasswordResetOTP resetOtp;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, otpRepository, mailSender, newPasswordIssuer,
                new OtpExpirationProperties(5));
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        resetOtp = new PasswordResetOTP();
        resetOtp.setOtp("123456");
        resetOtp.setUser(user);
        resetOtp.setExpiryDate(Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Requesting a reset by username saves a six-digit OTP valid for the configured minutes and emails it")
    void createPasswordResetOtp_byUsername_savesAndEmailsTheOtp() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.createPasswordResetOtp("alice");

        ArgumentCaptor<PasswordResetOTP> saved = ArgumentCaptor.forClass(PasswordResetOTP.class);
        verify(otpRepository).save(saved.capture());
        String otp = saved.getValue().getOtp();
        assertTrue(otp.matches("\\d{6}"));
        assertTrue(saved.getValue().getExpiryDate().isBefore(Instant.now().plus(Duration.ofMinutes(6))));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertArrayEquals(new String[]{"alice@example.com"}, mail.getValue().getTo());
        assertTrue(mail.getValue().getText().endsWith(otp));
    }

    @Test
    @DisplayName("Requesting a reset by email finds the user when no username matches")
    void createPasswordResetOtp_byEmail_findsTheUser() {
        when(userRepository.findByUsername("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.createPasswordResetOtp("alice@example.com");

        verify(otpRepository).save(any(PasswordResetOTP.class));
    }

    @Test
    @DisplayName("Requesting a reset for an unknown username or email is rejected and sends nothing")
    void createPasswordResetOtp_unknownIdentifier_isRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> service.createPasswordResetOtp("ghost"));
        verifyNoInteractions(otpRepository, mailSender);
    }

    @Test
    @DisplayName("Requesting a reset replaces the user's existing OTP")
    void createPasswordResetOtp_replacesTheExistingOtp() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(otpRepository.findByUser(user)).thenReturn(resetOtp);

        service.createPasswordResetOtp("alice");

        verify(otpRepository).delete(resetOtp);
        verify(otpRepository).save(any(PasswordResetOTP.class));
    }

    @Test
    @DisplayName("A valid OTP, given with the username or the email, gets the user a new password by email")
    void resetPassword_validOtp_issuesANewPassword() {
        when(otpRepository.findByOtp("123456")).thenReturn(resetOtp);

        service.resetPassword("alice", "123456");
        service.resetPassword("alice@example.com", "123456");

        verify(newPasswordIssuer, times(2)).issueNewPassword(user);
        verify(otpRepository, times(2)).delete(resetOtp);
    }

    @Test
    @DisplayName("An unknown OTP is rejected")
    void resetPassword_unknownOtp_isRejected() {
        when(otpRepository.findByOtp("000000")).thenReturn(null);

        InvalidPasswordResetOTPException exception = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword("alice", "000000"));
        assertEquals("Invalid OTP", exception.getMessage());
        verifyNoInteractions(newPasswordIssuer);
    }

    @Test
    @DisplayName("An OTP of another user is rejected")
    void resetPassword_otpOfAnotherUser_isRejected() {
        when(otpRepository.findByOtp("123456")).thenReturn(resetOtp);

        assertThrows(InvalidPasswordResetOTPException.class, () -> service.resetPassword("bob", "123456"));
        verifyNoInteractions(newPasswordIssuer);
        verify(otpRepository, never()).delete(any());
    }

    @Test
    @DisplayName("An expired OTP is rejected")
    void resetPassword_expiredOtp_isRejected() {
        resetOtp.setExpiryDate(Instant.now().minusSeconds(1));
        when(otpRepository.findByOtp("123456")).thenReturn(resetOtp);

        assertThrows(InvalidPasswordResetOTPException.class, () -> service.resetPassword("alice", "123456"));
        verifyNoInteractions(newPasswordIssuer);
    }
}
