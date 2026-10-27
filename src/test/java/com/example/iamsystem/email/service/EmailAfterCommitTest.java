package com.example.iamsystem.email.service;

import com.example.iamsystem.email.dto.EmailPurpose;
import com.example.iamsystem.email.dto.OutgoingEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmailService#sendAfterCommit} with the real event wiring, transactions and {@link EmailQueue}; only the mail
 * server is mocked.
 */
@SpringBootTest
class EmailAfterCommitTest {

    private static final long WAIT_MILLIS = 5_000;
    private static final long QUIET_MILLIS = 500;
    private static final OutgoingEmail EMAIL = new OutgoingEmail(EmailPurpose.PASSWORD_RESET_OTP, "alice@acme.com",
            Map.of("otp", "123456", "validMinutes", 5));

    @Autowired
    private EmailService emailService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    @Test
    @DisplayName("A queued email goes out only after its transaction commits, not on the caller's thread")
    void queuedEmail_isSentAfterTheCommit() {
        transactionTemplate.executeWithoutResult(status -> {
            emailService.sendAfterCommit(EMAIL);
            verify(mailSender, after(QUIET_MILLIS).never()).send(any(MimeMessage.class));
        });

        verify(mailSender, timeout(WAIT_MILLIS)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("A rolled-back transaction sends nothing")
    void rolledBackTransaction_sendsNothing() {
        transactionTemplate.executeWithoutResult(status -> {
            emailService.sendAfterCommit(EMAIL);
            status.setRollbackOnly();
        });

        verify(mailSender, after(QUIET_MILLIS).never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Queuing outside a transaction is refused, since the email would never go out")
    void outsideATransaction_isRefused() {
        assertThrows(IllegalStateException.class, () -> emailService.sendAfterCommit(EMAIL));
    }
}
