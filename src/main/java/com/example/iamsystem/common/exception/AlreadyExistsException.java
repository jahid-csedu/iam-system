package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Something with the same unique name or address already exists (400).
 */
public class AlreadyExistsException extends IamException {
    public AlreadyExistsException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
