package com.example.iamsystem.common.exception;

/**
 * The caller has to wait before trying again (429).
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
