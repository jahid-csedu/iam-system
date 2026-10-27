package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A protected endpoint was called without a usable access token (401); the message may say why the token is refused.
 */
public class AuthenticationRequiredException extends IamException {
    public AuthenticationRequiredException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
