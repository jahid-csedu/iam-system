package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A one-time code that is wrong, expired or used up (400).
 */
public class InvalidOtpException extends IamException {
    public InvalidOtpException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
