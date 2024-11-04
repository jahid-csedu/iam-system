package com.example.iamsystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class JwtException extends RuntimeException {
    public JwtException(String message) {
        super(message);
    }
}
