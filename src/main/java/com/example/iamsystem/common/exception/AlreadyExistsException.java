package com.example.iamsystem.common.exception;

/**
 * Something with the same unique identity already exists (answered with 400 Bad Request).
 */
public abstract class AlreadyExistsException extends RuntimeException {

    protected AlreadyExistsException(String message) {
        super(message);
    }
}
