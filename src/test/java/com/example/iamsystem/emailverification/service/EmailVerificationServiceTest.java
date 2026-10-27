package com.example.iamsystem.emailverification.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final long HOME_TENANT_ID = 10L;

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationCodeRepository codeRepository;
    @Mock
    private OtpRequestLimiter requestLimiter;
    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private EmailVerificationService service;
    private User alice;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(currentUserProvider, userRepository, codeRepository, new OneTimeCodes(),
                requestLimiter, passwordEncoder, emailService, new EmailVerificationProperties(30));
        alice = new User();
        alice.setId(7L);
        alice.setEmail("alice@acme.com");
        // A platform user working inside another tenant still verifies their own account in their home tenant.
        AuthenticatedActor actor = new AuthenticatedActor(7L, "alice", HOME_TENANT_ID, false, 1L, HOME_TENANT_ID)
                .actingIn(99L);
        lenient().when(currentUserProvider.requireCurrentActor()).thenReturn(actor);
        lenient().when(userRepository.getRequiredByIdAndTenantId(7L, HOME_TENANT_ID)).thenReturn(alice);
        lenient().when(requestLimiter.tryRecord(alice, OtpPurpose.EMAIL_VERIFICATION)).thenReturn(true);
    }

    @Test
    @DisplayName("A requested code replaces the pending one, is stored only as a hash and is emailed after the commit")
    void requestCode_savesTheHashAndQueuesTheEmail() {
        service.requestCode();

        verify(codeRepository).deleteByUser(alice);
        ArgumentCaptor<EmailVerificationCode> saved = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(codeRepository).save(saved.capture());
        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailService).sendAfterCommit(email.capture());
        String code = (String) email.getValue().values().get("code");
        assertEquals(EmailPurpose.EMAIL_VERIFICATION, email.getValue().purpose());
        assertEquals("alice@acme.com", email.getValue().to());
        assertEquals(30, email.getValue().values().get("validMinutes"));
        assertNotEquals(code, saved.getValue().getCodeHash());
        assertTrue(passwordEncoder.matches(code, saved.getValue().getCodeHash()));
        assertTrue(saved.getValue().getExpiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(31))));
    }

    @Test
    @DisplayName("Nothing to verify (no address, or already verified) and the hourly limit refuse the request and send nothing")
    void requestCode_isRefusedWhenThereIsNothingToSend() {
        alice.setEmailVerified(true);
        assertThrows(UserInputNotValidException.class, () -> service.requestCode());
        alice.setEmailVerified(false);
        alice.setEmail(null);
        assertThrows(UserInputNotValidException.class, () -> service.requestCode());
        verifyNoInteractions(requestLimiter);

        alice.setEmail("alice@acme.com");
        when(requestLimiter.tryRecord(alice, OtpPurpose.EMAIL_VERIFICATION)).thenReturn(false);
        assertThrows(TooManyRequestsException.class, () -> service.requestCode());
        verifyNoInteractions(codeRepository, emailService);
    }

    @Test
    @DisplayName("The right code verifies the address and is used up")
    void confirm_rightCode_verifiesTheAddress() {
        EmailVerificationCode pending = pending("123456", Instant.now().plusSeconds(60));

        service.confirm("123456");

        assertTrue(alice.isEmailVerified());
        verify(codeRepository).delete(pending);
    }

    @Test
    @DisplayName("A wrong code is counted and the last allowed guess deletes the code; nothing gets verified")
    void confirm_wrongCode_isCounted() {
        EmailVerificationCode pending = pending("123456", Instant.now().plusSeconds(60));

        assertThrows(InvalidOtpException.class, () -> service.confirm("654321"));
        assertEquals(1, pending.getFailedAttempts());
        verify(codeRepository, never()).delete(any());

        pending.setFailedAttempts(OneTimeCodes.MAX_FAILED_ATTEMPTS - 1);
        assertThrows(InvalidOtpException.class, () -> service.confirm("654321"));
        verify(codeRepository).delete(pending);
        assertFalse(alice.isEmailVerified());
    }

    @Test
    @DisplayName("No pending code and an expired code get the same error")
    void confirm_missingOrExpiredCode_isRefused() {
        when(codeRepository.findForUpdateByUser(alice)).thenReturn(Optional.empty());
        String missing = assertThrows(InvalidOtpException.class, () -> service.confirm("123456")).getMessage();

        EmailVerificationCode expired = pending("123456", Instant.now().minusSeconds(1));
        String late = assertThrows(InvalidOtpException.class, () -> service.confirm("123456")).getMessage();

        assertEquals(missing, late);
        verify(codeRepository).delete(expired);
        assertFalse(alice.isEmailVerified());
    }

    private EmailVerificationCode pending(String code, Instant expiresAt) {
        EmailVerificationCode pending = new EmailVerificationCode();
        pending.setUser(alice);
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setExpiresAt(expiresAt);
        when(codeRepository.findForUpdateByUser(alice)).thenReturn(Optional.of(pending));
        return pending;
    }
}
