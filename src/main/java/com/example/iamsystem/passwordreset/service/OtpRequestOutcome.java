package com.example.iamsystem.passwordreset.service;

/**
 * What happened to a password-reset request. It is only audited and logged: the caller always gets the same answer, so the
 * response never tells whether an account exists.
 */
public enum OtpRequestOutcome {
    SENT,
    UNKNOWN_USER,
    NO_EMAIL,
    MAIL_FAILED
}
