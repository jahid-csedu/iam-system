package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * An expected failure the caller is told about: its message is the answer's text, so it never contains a secret.
 * Each subclass stands for one kind of failure and its HTTP status; {@link GlobalHandler} answers all of them alike.
 */
public abstract class IamException extends RuntimeException {
    private final HttpStatus status;

    protected IamException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
