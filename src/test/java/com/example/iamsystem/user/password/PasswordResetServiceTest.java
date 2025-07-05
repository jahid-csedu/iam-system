package com.example.iamsystem.user.password;

import com.example.iamsystem.exception.DataNotFoundException;
import com.example.iamsystem.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.user.UserRepository;
import com.example.iamsystem.user.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetOTPRepository otpRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private PasswordEncoder passwordEncoder;

    private User user;
    private PasswordResetOTP passwordResetOTP;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("encodedPassword");

        passwordResetOTP = new PasswordResetOTP();
        passwordResetOTP.setId(1L);
        passwordResetOTP.setOtp("123456");
        passwordResetOTP.setUser(user);
        passwordResetOTP.setExpiryDate(new Date(System.currentTimeMillis() + 3600000).toInstant()); // 1 hour from now
    }

    @Test
    void createPasswordResetOtpForUser_successful() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(otpRepository.findByUser(any(User.class))).thenReturn(null);
        when(otpRepository.save(any(PasswordResetOTP.class))).thenReturn(passwordResetOTP);
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        passwordResetService.createPasswordResetOtpForUser(user.getEmail());

        verify(userRepository, times(1)).findByEmail(user.getEmail());
        verify(otpRepository, times(1)).findByUser(user);
        verify(otpRepository, times(1)).save(any(PasswordResetOTP.class));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void createPasswordResetOtpForUser_userNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> passwordResetService.createPasswordResetOtpForUser(user.getEmail()));

        verify(userRepository, times(1)).findByEmail(user.getEmail());
        verify(otpRepository, times(0)).findByUser(any(User.class));
        verify(otpRepository, times(0)).save(any(PasswordResetOTP.class));
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
    }

    @Test
    void createPasswordResetOtpForUser_existingOtpDeleted() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(otpRepository.findByUser(any(User.class))).thenReturn(passwordResetOTP);
        doNothing().when(otpRepository).delete(any(PasswordResetOTP.class));
        when(otpRepository.save(any(PasswordResetOTP.class))).thenReturn(passwordResetOTP);
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        passwordResetService.createPasswordResetOtpForUser(user.getEmail());

        verify(userRepository, times(1)).findByEmail(user.getEmail());
        verify(otpRepository, times(1)).findByUser(user);
        verify(otpRepository, times(1)).delete(passwordResetOTP);
        verify(otpRepository, times(1)).save(any(PasswordResetOTP.class));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetPassword_successful() {
        passwordResetOTP.setExpiryDate(Instant.now().plusSeconds(1000));
        when(otpRepository.findByOtp(anyString())).thenReturn(passwordResetOTP);
        when(passwordEncoder.encode(anyString())).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        doNothing().when(otpRepository).delete(any(PasswordResetOTP.class));

        passwordResetService.resetPassword(passwordResetOTP.getOtp(), user.getEmail());

        verify(otpRepository, times(1)).findByOtp(passwordResetOTP.getOtp());
        verify(passwordEncoder, times(1)).encode(anyString());
        verify(userRepository, times(1)).save(user);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        verify(otpRepository, times(1)).delete(passwordResetOTP);
    }

    @Test
    void resetPassword_invalidOtp() {
        when(otpRepository.findByOtp(anyString())).thenReturn(null);

        assertThrows(InvalidPasswordResetOTPException.class, () -> passwordResetService.resetPassword("wrongotp", user.getEmail()));

        verify(otpRepository, times(1)).findByOtp(anyString());
        verify(passwordEncoder, times(0)).encode(anyString());
        verify(userRepository, times(0)).save(any(User.class));
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
        verify(otpRepository, times(0)).delete(any(PasswordResetOTP.class));
    }

    @Test
    void resetPassword_otpDoesNotBelongToUser() {
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        passwordResetOTP.setUser(anotherUser);

        when(otpRepository.findByOtp(anyString())).thenReturn(passwordResetOTP);

        assertThrows(InvalidPasswordResetOTPException.class, () -> passwordResetService.resetPassword(passwordResetOTP.getOtp(), user.getEmail()));

        verify(otpRepository, times(1)).findByOtp(anyString());
        verify(passwordEncoder, times(0)).encode(anyString());
        verify(userRepository, times(0)).save(any(User.class));
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
        verify(otpRepository, times(0)).delete(any(PasswordResetOTP.class));
    }

    @Test
    void resetPassword_otpExpired() {
        passwordResetOTP.setExpiryDate(Instant.now().minusSeconds(1000));

        when(otpRepository.findByOtp(anyString())).thenReturn(passwordResetOTP);

        assertThrows(InvalidPasswordResetOTPException.class, () -> passwordResetService.resetPassword(passwordResetOTP.getOtp(), user.getEmail()));

        verify(otpRepository, times(1)).findByOtp(anyString());
        verify(passwordEncoder, times(0)).encode(anyString());
        verify(userRepository, times(0)).save(any(User.class));
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
        verify(otpRepository, times(0)).delete(any(PasswordResetOTP.class));
    }
}
