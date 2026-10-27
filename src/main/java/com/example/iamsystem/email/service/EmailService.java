package com.example.iamsystem.email.service;

import com.example.iamsystem.email.config.EmailProperties;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.dto.QueuedEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The one owner of sending email: every feature hands over an {@link OutgoingEmail} and nothing else touches
 * {@link JavaMailSender}. The email has an HTML part and a plain-text part, rendered from the purpose's templates, and
 * comes from {@code iam.mail.from}. Its content (which may hold an OTP) is never logged.
 * <p>
 * Two ways to send: {@link #sendAfterCommit} (the default: the request never waits for the mail server, and nothing is
 * sent when it rolls back) and {@link #send} (at once, for a caller that must know the email went out before it
 * changes anything).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine emailTemplateEngine;
    private final EmailProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Queues the email to be sent once the caller's transaction commits, on the {@link EmailQueue}'s threads. A failure
     * then is only logged.
     *
     * @throws IllegalStateException outside a transaction, where the email would never be sent
     */
    public void sendAfterCommit(OutgoingEmail email) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A " + email.purpose() + " email can only be queued inside a transaction");
        }
        eventPublisher.publishEvent(new QueuedEmail(email));
    }

    /**
     * Sends at once, inside the caller's request.
     *
     * @throws MailException when the email can't be built or the mail server refuses or doesn't answer
     */
    public void send(OutgoingEmail email) {
        mailSender.send(toMimeMessage(email));
        log.info("Sent a {} email", email.purpose());
    }

    private MimeMessage toMimeMessage(OutgoingEmail email) {
        Context context = new Context(Locale.ENGLISH, email.values());
        String html = emailTemplateEngine.process(email.purpose().htmlTemplate(), context);
        String text = emailTemplateEngine.process(email.purpose().textTemplate(), context);
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(email.to());
            helper.setSubject(email.purpose().subject());
            helper.setText(text, html);
        } catch (MessagingException e) {
            throw new MailPreparationException("Could not build a " + email.purpose() + " email", e);
        }
        return message;
    }
}
