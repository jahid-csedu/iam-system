package com.example.iamsystem.common.exception;

/**
 * A public request (login, token refresh, password reset) named a client_id that doesn't exist (answered with 401).
 */
public class InvalidClientException extends RuntimeException {

    public InvalidClientException(String message) {
        super(message);
    }
}
