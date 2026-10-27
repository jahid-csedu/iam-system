package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * An email the operation depends on could not be sent, so the operation was not done; the caller may try again later
 * (503).
 */
public class EmailNotSentException extends IamException {
    public EmailNotSentException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
