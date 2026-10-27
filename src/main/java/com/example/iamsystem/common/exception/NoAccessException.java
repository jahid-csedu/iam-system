package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller may not do this: a missing permission or a rule such as "no changes to your own access" (403).
 */
public class NoAccessException extends IamException {
    public NoAccessException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
