package com.example.iamsystem.common.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorMessage {
    public static final String USERNAME_REQUIRED = "Username is required";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String USERNAME_NOT_AVAILABLE = "Username not available";
    public static final String EMAIL_NOT_AVAILABLE = "Email not available";
    public static final String USER_NOT_LOGGED_IN = "No logged in user found";
    public static final String PASSWORD_REQUIRED = "Password is required";
    public static final String INVALID_EMAIL = "Email should be a valid email address";
    public static final String INVALID_TOKEN = "Invalid Token";
    public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
    public static final String ROLE_NOT_FOUND = "Role not found";
    public static final String CLIENT_NOT_FOUND = "Client not found";
    public static final String CLIENT_EXISTS = "Client name already exists";
    public static final String CLIENT_NAME_REQUIRED = "Client name cannot be null or blank";
    public static final String CLIENT_ID_REQUIRED = "client_id is required";
    public static final String INVALID_CLIENT = "Unknown client_id";
    public static final String CONSOLE_CLIENT_NOT_DELETABLE = "The management console client can't be deleted";
    public static final String CONSOLE_CLIENT_MANAGEMENT_ONLY = "Only management users can log in through the management console";
    public static final String ROLES_NOT_FOUND = "Some roles not found";
    public static final String ROLE_EXISTS = "Role name already exists";
    public static final String ROLE_ID_REQUIRED = "Role id is required";
    public static final String OWN_ACCESS_CHANGE = "Users cannot change their own roles or permissions";
    public static final String REGULAR_USER_ACCESS = "Regular users cannot hold roles or permissions";
    public static final String HELD_ROLE_CHANGE = "Users cannot change a role they hold";
    public static final String PERMISSION_NOT_FOUND = "Permission not found";
    public static final String PERMISSIONS_NOT_FOUND = "Some permissions not found";
    public static final String TENANT_EXISTS = "Tenant name not available";
    public static final String TENANT_NAME_REQUIRED = "Tenant name is required";
    public static final String ROOT_USER_REQUIRED = "Root user details are required";
    public static final String PERMISSION_LIST_NOT_EMPTY = "Permission list cannot be empty";
    public static final String ROLE_LIST_REQUIRED = "Role list cannot be null";
    public static final String ROLE_LIST_NOT_EMPTY = "Role list cannot be empty";
    public static final String ROLE_NAME_REQUIRED = "Role name cannot be null or blank";
    public static final String TOKEN_REQUIRED = "Token cannot be null or blank";
    public static final String NO_PERMISSION = "Do not have permission to perform this operation";
    public static final String PASSWORD_POLICY_VIOLATION = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character.";
    public static final String ACCOUNT_DISABLED = "Account is disabled";
    public static final String ACCOUNT_LOCKED = "Account is locked. Please try again later.";
    public static final String INVALID_OLD_PASSWORD = "Invalid old password";
    public static final String OLD_PASSWORD_REQUIRED = "Old password is required";
    public static final String NEW_PASSWORD_REQUIRED = "New password is required";
    public static final String OWN_PASSWORD_RESET = "Users cannot reset their own password; change it instead";
    public static final String UNREADABLE_REQUEST_BODY = "Request body is missing or malformed";
    public static final String IDENTIFIER_REQUIRED = "Username or email is required";
    public static final String OTP_REQUIRED = "OTP is required";
    public static final String USER_HAS_NO_EMAIL = "User has no email address to send a new password to";
}

