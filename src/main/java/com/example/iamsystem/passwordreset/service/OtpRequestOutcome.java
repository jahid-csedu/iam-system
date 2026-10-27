package com.example.iamsystem.passwordreset.service;

/**
 * What happened to a password-reset request. It is only audited and logged: the caller always gets the same answer, so the
 * response never tells whether an account exists.
 */
public enum OtpRequestOutcome {
    /**
     * Sent at once (a reset by another user).
     */
    SENT,
    /**
     * To be sent after commit (the user's own request); a later mail failure is only logged.
     */
    QUEUED,
    UNKNOWN_USER,
    NO_EMAIL,
    TOO_MANY_REQUESTS,
    MAIL_FAILED
}
