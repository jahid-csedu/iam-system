package com.example.iamsystem.emailverification.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How long an email verification code stays valid ({@code iam.email-verification.code-minutes}, default 30).
 */
@Validated
@ConfigurationProperties("iam.email-verification")
public record EmailVerificationProperties(@DefaultValue("30") @Positive int codeMinutes) {
}
