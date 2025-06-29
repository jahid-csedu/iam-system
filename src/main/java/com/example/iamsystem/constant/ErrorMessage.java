package com.example.iamsystem.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessage {
    public static final String USERNAME_REQUIRED = "Username is required";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String PASSWORD_REQUIRED = "Password is required";
    public static final String PASSWORD_LENGTH = "Password should be between 6 to 15 characters";
    public static final String INVALID_EMAIL = "Email should be a valid email address";
    public static final String INVALID_TOKEN = "Invalid Token";
    public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
    public static final String ROLE_NOT_FOUND = "Role not found";
    public static final String PERMISSION_NOT_FOUND = "Permission not found";
    public static final String PERMISSION_EXISTS = "Permission already exists";
    public static final String SERVICE_NAME_REQUIRED = "Service name cannot be null or blank";
    public static final String ACTION_NAME_REQUIRED = "Action cannot be null or blank";
    public static final String PERMISSION_LIST_REQUIRED = "Permission list cannot be null";
    public static final String PERMISSION_LIST_NOT_EMPTY = "Permission list cannot be empty";
    public static final String ROLE_LIST_REQUIRED = "Role list cannot be null";
    public static final String ROLE_LIST_NOT_EMPTY = "Role list cannot be empty";
    public static final String ROLE_NAME_REQUIRED = "Role name cannot be null or blank";
    public static final String TOKEN_REQUIRED = "Token cannot be null or blank";
    public static final String NO_PERMISSION = "Do not have permission to perform this operation";
    public static final String PASSWORD_POLICY_VIOLATION = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character.";
    public static final String ACCOUNT_LOCKED = "Account is locked. Please try again later.";
}

