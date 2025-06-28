package com.example.iamsystem.exception;

public class UserInputNotValidException extends RuntimeException{
    public UserInputNotValidException(String message) {
        super(message);
    }
}
