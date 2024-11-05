package com.example.iamsystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class NoAccessException extends RuntimeException {
    public NoAccessException(String message) {
        super(message);
    }
}
