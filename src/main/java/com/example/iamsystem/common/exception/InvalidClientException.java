package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A public request (login, token refresh, password reset) named a client_id that doesn't exist (401).
 */
public class InvalidClientException extends IamException {
    public InvalidClientException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
