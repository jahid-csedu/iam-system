package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A request that is well formed but can't be carried out as asked (400).
 */
public class UserInputNotValidException extends IamException {
    public UserInputNotValidException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
