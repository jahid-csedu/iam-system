package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.passwordreset.config.OtpExpirationProperties;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String CLIENT_ID = "app-client-id-0000000001";
    private static final Long TENANT_ID = 100L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetOTPRepository otpRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private NewPasswordIssuer newPasswordIssuer;
    @Mock
    private ClientResolver clientResolver;
    @Mock
    private ResetRequestLimiter requestLimiter;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private PasswordResetService service;
    private User user;
    private PasswordResetOTP resetOtp;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, otpRepository, mailSender, newPasswordIssuer, clientResolver,
                new OtpExpirationProperties(5), requestLimiter, passwordEncoder);
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(TENANT_ID);
        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setTenant(tenant);
        lenient().when(clientResolver.resolve(CLIENT_ID)).thenReturn(client);
        lenient().when(requestLimiter.tryRecord(any(User.class))).thenReturn(true);

        user = new User();
        user.setId(1L);
        user.setTenant(tenant);
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        resetOtp = new PasswordResetOTP();
        resetOtp.setOtpHash(passwordEncoder.encode("123456"));
        resetOtp.setUser(user);
        resetOtp.setExpiryDate(Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Requesting a reset by username in the client's tenant saves a six-digit OTP valid for the configured minutes and emails it")
    void createPasswordResetOtp_byUsername_savesAndEmailsTheOtp() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));

        assertEquals(OtpRequestOutcome.SENT, service.createPasswordResetOtp(CLIENT_ID, "alice"));

        ArgumentCaptor<PasswordResetOTP> saved = ArgumentCaptor.forClass(PasswordResetOTP.class);
        verify(otpRepository).save(saved.capture());
        String hash = saved.getValue().getOtpHash();
        assertTrue(saved.getValue().getExpiryDate().isBefore(Instant.now().plus(Duration.ofMinutes(6))));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertArrayEquals(new String[]{"alice@example.com"}, mail.getValue().getTo());
        String otp = mail.getValue().getText().substring(mail.getValue().getText().length() - 6);
        assertTrue(otp.matches("\\d{6}"));
        assertNotEquals(otp, hash);
        assertTrue(passwordEncoder.matches(otp, hash));
    }

    @Test
    @DisplayName("Requesting a reset by email finds the user when no username matches")
    void createPasswordResetOtp_byEmail_findsTheUser() {
        when(userRepository.findByUsernameAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.of(user));

        service.createPasswordResetOtp(CLIENT_ID, "alice@example.com");

        verify(otpRepository).save(any(PasswordResetOTP.class));
    }

    @Test
    @DisplayName("Requesting a reset for an unknown username or email is no error, and sends nothing")
    void createPasswordResetOtp_unknownIdentifier_sendsNothing() {
        when(userRepository.findByUsernameAndTenantId("ghost", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("ghost", TENANT_ID)).thenReturn(Optional.empty());

        assertEquals(OtpRequestOutcome.UNKNOWN_USER, service.createPasswordResetOtp(CLIENT_ID, "ghost"));
        verifyNoInteractions(otpRepository, mailSender);
    }

    @Test
    @DisplayName("Requesting a reset for a user without an email address is no error, and saves and sends nothing")
    void createPasswordResetOtp_userWithoutEmail_sendsNothing() {
        user.setEmail(null);
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));

        assertEquals(OtpRequestOutcome.NO_EMAIL, service.createPasswordResetOtp(CLIENT_ID, "alice"));
        verifyNoInteractions(otpRepository, mailSender);
    }

    @Test
    @DisplayName("A failed email is no error: the outcome says so")
    void createPasswordResetOtp_mailFailure_isReported() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertEquals(OtpRequestOutcome.MAIL_FAILED, service.createPasswordResetOtp(CLIENT_ID, "alice"));
    }

    @Test
    @DisplayName("Over the request limit, no OTP is made and nothing is sent")
    void createPasswordResetOtp_overTheLimit_sendsNothing() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(requestLimiter.tryRecord(user)).thenReturn(false);

        assertEquals(OtpRequestOutcome.TOO_MANY_REQUESTS, service.createPasswordResetOtp(CLIENT_ID, "alice"));
        verifyNoInteractions(otpRepository, mailSender);
    }

    @Test
    @DisplayName("Requesting a reset replaces the user's existing OTP")
    void createPasswordResetOtp_replacesTheExistingOtp() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findByUser(user)).thenReturn(Optional.of(resetOtp));

        service.createPasswordResetOtp(CLIENT_ID, "alice");

        verify(otpRepository).delete(resetOtp);
        verify(otpRepository).save(any(PasswordResetOTP.class));
    }

    @Test
    @DisplayName("A valid OTP, given with the username or the email, gets the user a new password by email")
    void resetPassword_validOtp_issuesANewPassword() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));

        service.resetPassword(CLIENT_ID, "alice", "123456");
        service.resetPassword(CLIENT_ID, "alice@example.com", "123456");

        verify(newPasswordIssuer, times(2)).issueNewPassword(user);
        verify(otpRepository, times(2)).delete(resetOtp);
    }

    @Test
    @DisplayName("An unknown user, a user without a pending OTP and an expired OTP get the same error")
    void resetPassword_noUsableOtp_isRejectedTheSameWay() {
        when(userRepository.findByUsernameAndTenantId("ghost", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("ghost", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.empty());

        String unknownUser = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword(CLIENT_ID, "ghost", "123456")).getMessage();
        String noOtp = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword(CLIENT_ID, "alice", "123456")).getMessage();
        resetOtp.setExpiryDate(Instant.now().minusSeconds(1));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));
        String expired = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword(CLIENT_ID, "alice", "123456")).getMessage();

        assertEquals("Invalid or expired OTP", unknownUser);
        assertEquals(unknownUser, noOtp);
        assertEquals(unknownUser, expired);
        verify(otpRepository).delete(resetOtp);
        verifyNoInteractions(newPasswordIssuer);
    }

    @Test
    @DisplayName("A wrong OTP is counted and rejected with the same error, and the OTP is kept")
    void resetPassword_wrongOtp_isCounted() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));

        InvalidPasswordResetOTPException exception = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword(CLIENT_ID, "alice", "654321"));

        assertEquals("Invalid or expired OTP", exception.getMessage());
        assertEquals(1, resetOtp.getFailedAttempts());
        verify(otpRepository, never()).delete(any());
        verifyNoInteractions(newPasswordIssuer);
    }

    @Test
    @DisplayName("The last allowed wrong guess deletes the OTP")
    void resetPassword_lastWrongGuess_deletesTheOtp() {
        resetOtp.setFailedAttempts(PasswordResetService.MAX_FAILED_ATTEMPTS - 1);
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));

        assertThrows(InvalidPasswordResetOTPException.class, () -> service.resetPassword(CLIENT_ID, "alice", "654321"));

        verify(otpRepository).delete(resetOtp);
        verifyNoInteractions(newPasswordIssuer);
    }

    @Test
    @DisplayName("The OTP of a user in another tenant is never looked at")
    void resetPassword_userOfAnotherTenant_isNotFound() {
        when(userRepository.findByUsernameAndTenantId("bob", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("bob", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetOTPException.class, () -> service.resetPassword(CLIENT_ID, "bob", "123456"));
        verifyNoInteractions(otpRepository, newPasswordIssuer);
    }
}
