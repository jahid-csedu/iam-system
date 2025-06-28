package com.example.iamsystem.exception;

public class PermissionAlreadyExistsException extends RuntimeException {
    public PermissionAlreadyExistsException(String message) {
        super(message);
    }
}
