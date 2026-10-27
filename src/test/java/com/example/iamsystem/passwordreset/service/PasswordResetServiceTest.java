package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.InvalidPasswordResetOTPException;
import com.example.iamsystem.passwordreset.entity.PasswordResetOTP;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.session.service.SessionService;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserPasswordUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private UserPasswordUpdater passwordUpdater;
    @Mock
    private SessionService sessionService;
    @Mock
    private ClientResolver clientResolver;
    @Mock
    private ResetOtpIssuer otpIssuer;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private PasswordResetService service;
    private User user;
    private PasswordResetOTP resetOtp;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, otpRepository, clientResolver, otpIssuer, passwordEncoder,
                passwordUpdater, sessionService);
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(TENANT_ID);
        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setTenant(tenant);
        lenient().when(clientResolver.resolve(CLIENT_ID)).thenReturn(client);

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
    @DisplayName("Requesting a reset by username in the client's tenant queues an OTP email for after the commit")
    void createPasswordResetOtp_byUsername_issuesAnOtp() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpIssuer.issueAfterCommit(user)).thenReturn(OtpRequestOutcome.QUEUED);

        assertEquals(OtpRequestOutcome.QUEUED, service.createPasswordResetOtp(CLIENT_ID, "alice"));
    }

    @Test
    @DisplayName("Requesting a reset by email finds the user when no username matches")
    void createPasswordResetOtp_byEmail_findsTheUser() {
        when(userRepository.findByUsernameAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.of(user));

        service.createPasswordResetOtp(CLIENT_ID, "alice@example.com");

        verify(otpIssuer).issueAfterCommit(user);
    }

    @Test
    @DisplayName("Requesting a reset for an unknown username or email is no error, sends nothing and takes as long")
    void createPasswordResetOtp_unknownIdentifier_sendsNothing() {
        when(userRepository.findByUsernameAndTenantId("ghost", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("ghost", TENANT_ID)).thenReturn(Optional.empty());

        assertEquals(OtpRequestOutcome.UNKNOWN_USER, service.createPasswordResetOtp(CLIENT_ID, "ghost"));
        verify(otpIssuer).spendIssuingTime();
        verifyNoMoreInteractions(otpIssuer);
    }

    @Test
    @DisplayName("Whatever happened to the OTP, the outcome is passed on, never thrown")
    void createPasswordResetOtp_passesTheOutcomeOn() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpIssuer.issueAfterCommit(user)).thenReturn(OtpRequestOutcome.TOO_MANY_REQUESTS);

        assertEquals(OtpRequestOutcome.TOO_MANY_REQUESTS, service.createPasswordResetOtp(CLIENT_ID, "alice"));
    }

    @Test
    @DisplayName("A valid OTP, given with the username or the email, sets the chosen password and ends every session")
    void resetPassword_validOtp_setsTheNewPassword() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("alice@example.com", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));

        service.resetPassword(CLIENT_ID, "alice", "123456", "NewPassword1!");
        service.resetPassword(CLIENT_ID, "alice@example.com", "123456", "NewPassword1!");

        verify(passwordUpdater, times(2)).updatePassword(user, "NewPassword1!");
        verify(sessionService, times(2)).revokeAllOf(1L);
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
                () -> service.resetPassword(CLIENT_ID, "ghost", "123456", "NewPassword1!")).getMessage();
        String noOtp = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword(CLIENT_ID, "alice", "123456", "NewPassword1!")).getMessage();
        resetOtp.setExpiryDate(Instant.now().minusSeconds(1));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));
        String expired = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword(CLIENT_ID, "alice", "123456", "NewPassword1!")).getMessage();

        assertEquals("Invalid or expired OTP", unknownUser);
        assertEquals(unknownUser, noOtp);
        assertEquals(unknownUser, expired);
        verify(otpRepository).delete(resetOtp);
        verifyNoInteractions(passwordUpdater, sessionService);
    }

    @Test
    @DisplayName("A wrong OTP is counted and rejected with the same error, and the OTP is kept")
    void resetPassword_wrongOtp_isCounted() {
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));

        InvalidPasswordResetOTPException exception = assertThrows(InvalidPasswordResetOTPException.class,
                () -> service.resetPassword(CLIENT_ID, "alice", "654321", "NewPassword1!"));

        assertEquals("Invalid or expired OTP", exception.getMessage());
        assertEquals(1, resetOtp.getFailedAttempts());
        verify(otpRepository, never()).delete(any());
        verifyNoInteractions(passwordUpdater, sessionService);
    }

    @Test
    @DisplayName("The last allowed wrong guess deletes the OTP")
    void resetPassword_lastWrongGuess_deletesTheOtp() {
        resetOtp.setFailedAttempts(PasswordResetService.MAX_FAILED_ATTEMPTS - 1);
        when(userRepository.findByUsernameAndTenantId("alice", TENANT_ID)).thenReturn(Optional.of(user));
        when(otpRepository.findForUpdateByUser(user)).thenReturn(Optional.of(resetOtp));

        assertThrows(InvalidPasswordResetOTPException.class, () -> service.resetPassword(CLIENT_ID, "alice", "654321", "NewPassword1!"));

        verify(otpRepository).delete(resetOtp);
        verifyNoInteractions(passwordUpdater, sessionService);
    }

    @Test
    @DisplayName("The OTP of a user in another tenant is never looked at")
    void resetPassword_userOfAnotherTenant_isNotFound() {
        when(userRepository.findByUsernameAndTenantId("bob", TENANT_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("bob", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetOTPException.class, () -> service.resetPassword(CLIENT_ID, "bob", "123456", "NewPassword1!"));
        verifyNoInteractions(otpRepository, passwordUpdater);
    }
}
