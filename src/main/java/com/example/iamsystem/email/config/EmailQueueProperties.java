package com.example.iamsystem.email.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The queue of emails sent after commit ({@code iam.mail.queue.*}): {@code threads} send in parallel, and up to
 * {@code capacity} more wait; beyond that an email is dropped with an ERROR log.
 */
@Validated
@ConfigurationProperties("iam.mail.queue")
public record EmailQueueProperties(@DefaultValue("2") @Positive int threads, @DefaultValue("100") @Positive int capacity) {
}
