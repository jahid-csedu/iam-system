package com.example.iamsystem.email.dto;

/**
 * The event of an email to send once the current transaction commits ({@code EmailService.sendAfterCommit}). Its
 * {@code toString} is the masked one of {@link OutgoingEmail}.
 */
public record QueuedEmail(OutgoingEmail email) {
}
