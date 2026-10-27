package com.example.iamsystem.email.service;

import com.example.iamsystem.email.config.EmailQueueProperties;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.dto.QueuedEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the emails of {@link EmailService#sendAfterCommit} once their transaction has committed, on threads of its own,
 * so the request never waits for the mail server and a rolled-back request sends nothing. A failed or dropped email is
 * logged (ERROR) and not retried; the user can ask again.
 * <p>
 * The thread pool is this component's own, not a Spring bean: a second {@code Executor} bean would make Spring Boot drop
 * the default one that other {@code @Async} methods (the audit log) run on.
 */
@Component
@Slf4j
public class EmailQueue implements DisposableBean {

    static final String THREAD_NAME_PREFIX = "email-";
    /**
     * At shutdown, queued emails get this long to go out (an unresponsive mail server times out after 10 s).
     */
    static final int SHUTDOWN_WAIT_SECONDS = 30;

    private final EmailService emailService;
    private final ThreadPoolTaskExecutor executor;

    public EmailQueue(EmailService emailService, EmailQueueProperties properties) {
        this.emailService = emailService;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.threads());
        executor.setMaxPoolSize(properties.threads());
        executor.setQueueCapacity(properties.capacity());
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler((task, pool) -> log.error("Email queue full: an email was dropped"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        executor.initialize();
    }

    /**
     * Runs after the publishing transaction committed; never for a rolled-back one.
     */
    @TransactionalEventListener
    public void onCommitted(QueuedEmail queued) {
        executor.execute(() -> deliver(queued.email()));
    }

    private void deliver(OutgoingEmail email) {
        try {
            emailService.send(email);
        } catch (RuntimeException e) {
            log.error("Could not send a {} email", email.purpose(), e);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
