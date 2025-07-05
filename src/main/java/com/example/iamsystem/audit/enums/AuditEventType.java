package com.example.iamsystem.audit.enums;

public enum AuditEventType {
    // Authentication
    USER_LOGIN,
    TOKEN_REFRESH,

    // User Management
    USER_REGISTRATION,
    PASSWORD_CHANGE,
    ROLES_ASSIGNMENT,
    ROLES_REMOVAL,
    USER_DELETE,

    // Role Management
    ROLE_CREATION,
    ROLE_UPDATE,
    ROLE_DELETE,
    PERMISSIONS_ASSIGNED_TO_ROLE,
    PERMISSIONS_REMOVED_FROM_ROLE,

    // Permission Management
    PERMISSION_CREATE,
    PERMISSION_UPDATE,
    PERMISSION_DELETE,

    // Password Reset
    PASSWORD_RESET_OTP_REQUEST,
    PASSWORD_RESET
}