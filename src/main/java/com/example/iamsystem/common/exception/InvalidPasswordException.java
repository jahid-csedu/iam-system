package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A password that is wrong (old or current password) or breaks the password policy (400).
 */
public class InvalidPasswordException extends IamException {
    public InvalidPasswordException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
