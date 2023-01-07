package com.example.management.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UserInputNotValidException extends RuntimeException{
    public UserInputNotValidException(String message) {
        super(message);
    }
}
