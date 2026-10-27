package com.example.iamsystem.email.service;

import com.example.iamsystem.email.config.EmailProperties;
import com.example.iamsystem.email.config.EmailTemplateConfig;
import com.example.iamsystem.email.dto.EmailPurpose;
import com.example.iamsystem.email.dto.OutgoingEmail;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final OutgoingEmail RESET_OTP_EMAIL = new OutgoingEmail(EmailPurpose.PASSWORD_RESET_OTP, "alice@acme.com",
            Map.of("otp", "042917", "validMinutes", 5));

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, new EmailTemplateConfig().emailTemplateEngine(),
                new EmailProperties("IAM System <no-reply@acme.com>"));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    @DisplayName("An email goes from the configured sender to the recipient, with the purpose's subject")
    void send_addressesTheEmail() throws Exception {
        MimeMessage sent = send(RESET_OTP_EMAIL);

        assertArrayEquals(new InternetAddress[]{new InternetAddress("IAM System <no-reply@acme.com>")}, sent.getFrom());
        assertArrayEquals(new InternetAddress[]{new InternetAddress("alice@acme.com")}, sent.getRecipients(Message.RecipientType.TO));
        assertEquals("Your password reset code", sent.getSubject());
    }

    @Test
    @DisplayName("The email has a plain-text and an HTML part, both rendered from the purpose's templates with its values")
    void send_rendersBothParts() throws Exception {
        MimeMessage sent = send(RESET_OTP_EMAIL);
        sent.saveChanges();
        Map<String, String> parts = new HashMap<>();
        collectParts(sent, parts);

        String text = parts.get("text/plain");
        String html = parts.get("text/html");
        assertTrue(text.contains("042917"), text);
        assertTrue(text.contains("valid for 5 minutes"), text);
        assertTrue(html.contains(">042917<"), html);
        assertTrue(html.contains("<span>5</span> minutes"), html);
        assertTrue(html.contains("IAM System"), html);
        assertTrue(html.contains("<title>Your password reset code</title>"), html);
    }

    @Test
    @DisplayName("A mail server failure reaches the caller")
    void send_mailFailure_isThrown() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () -> service.send(RESET_OTP_EMAIL));
    }

    private MimeMessage send(OutgoingEmail email) {
        service.send(email);
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }

    private static void collectParts(Part part, Map<String, String> parts) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                collectParts(bodyPart, parts);
            }
        } else if (part.isMimeType("text/plain")) {
            parts.put("text/plain", (String) content);
        } else if (part.isMimeType("text/html")) {
            parts.put("text/html", (String) content);
        }
    }
}
