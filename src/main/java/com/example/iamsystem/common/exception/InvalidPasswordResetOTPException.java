package com.example.iamsystem.common.exception;



public class InvalidPasswordResetOTPException extends RuntimeException {

    public InvalidPasswordResetOTPException(String message) {
        super(message);
    }
}
