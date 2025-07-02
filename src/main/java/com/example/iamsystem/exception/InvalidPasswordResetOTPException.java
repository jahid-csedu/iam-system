package com.example.iamsystem.exception;

public class InvalidPasswordResetOTPException extends RuntimeException {

    public InvalidPasswordResetOTPException(String message) {
        super(message);
    }
}
