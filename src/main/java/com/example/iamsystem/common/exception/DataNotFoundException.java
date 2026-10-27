package com.example.iamsystem.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The thing asked for doesn't exist in the caller's tenant (404); another tenant's data is never reported as
 * forbidden.
 */
public class DataNotFoundException extends IamException {
    public DataNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
