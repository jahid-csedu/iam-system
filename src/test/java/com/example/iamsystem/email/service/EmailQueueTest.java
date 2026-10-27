package com.example.iamsystem.email.service;

import com.example.iamsystem.email.config.EmailQueueProperties;
import com.example.iamsystem.email.dto.EmailPurpose;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.dto.QueuedEmail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailQueueTest {

    private static final long WAIT_MILLIS = 5_000;

    @Mock
    private EmailService emailService;

    private EmailQueue queue;

    @BeforeEach
    void setUp() {
        queue = new EmailQueue(emailService, new EmailQueueProperties(1, 10));
    }

    @AfterEach
    void tearDown() {
        queue.destroy();
    }

    @Test
    @DisplayName("A committed email is sent on the queue's thread")
    void onCommitted_sendsTheEmail() {
        OutgoingEmail email = email("alice@acme.com");

        queue.onCommitted(new QueuedEmail(email));

        verify(emailService, timeout(WAIT_MILLIS)).send(email);
    }

    @Test
    @DisplayName("A failed email is only logged: the queue keeps sending the next ones")
    void failedEmail_doesNotStopTheQueue() {
        OutgoingEmail failing = email("alice@acme.com");
        OutgoingEmail next = email("bob@acme.com");
        doThrow(new MailSendException("SMTP down")).when(emailService).send(failing);

        queue.onCommitted(new QueuedEmail(failing));
        queue.onCommitted(new QueuedEmail(next));

        verify(emailService, timeout(WAIT_MILLIS)).send(next);
    }

    private static OutgoingEmail email(String to) {
        return new OutgoingEmail(EmailPurpose.PASSWORD_RESET_OTP, to, Map.of("otp", "123456", "validMinutes", 5));
    }
}
