package com.example.iamsystem.common.exception;

/**
 * An email the operation depends on could not be sent, so the operation was not done; the caller may try again later
 * (503).
 */
public class EmailNotSentException extends RuntimeException {

    public EmailNotSentException(String message) {
        super(message);
    }
}
