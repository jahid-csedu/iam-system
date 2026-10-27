package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller has to wait before trying again (429).
 */
public class TooManyRequestsException extends IamException {
    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
