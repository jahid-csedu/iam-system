package com.example.management.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessage {
    public static final String USERNAME_REQUIRED = "Username is required";
    public static final String PASSWORD_REQUIRED = "Password is required";
    public static final String PASSWORD_LENGTH = "Password should be between 6 to 15 characters";
    public static final String INVALID_EMAIL = "Email should be a valid email address";
    public static final String INVALID_TOKEN = "Invalid Token";
    public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
    public static final String ROLE_NOT_FOUND = "Role not found";
    public static final String PERMISSION_NOT_FOUND = "Permission not found";
    public static final String SERVICE_NAME_REQUIRED = "Service name cannot be null or blank";
    public static final String ACTION_NAME_REQUIRED = "Action cannot be null or blank";
    public static final String PERMISSION_ID_REQUIRED = "Permission list cannot be null";
    public static final String PERMISSION_ID_NOT_EMPTY = "Permission list cannot be empty";
    public static final String ROLE_ID_REQUIRED = "Role list cannot be null";
    public static final String ROLE_ID_NOT_EMPTY = "Role list cannot be empty";
    public static final String ROLE_NAME_REQUIRED = "Role name cannot be null or blank";
}
