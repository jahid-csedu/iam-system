package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A refresh token that is unknown, expired, already used, revoked or presented through another client (401).
 */
public class InvalidTokenException extends IamException {
    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
