package com.example.iamsystem.common.exception;

/**
 * A refresh token that is unknown, expired, already used, revoked or presented through another client (401).
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
