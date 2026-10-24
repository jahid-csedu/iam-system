package com.example.iamsystem.audit.enums;

public enum AuditEventType {
    // Authentication
    USER_LOGIN,
    TOKEN_REFRESH,

    // Tenant Management
    TENANT_SIGNUP,

    // User Management
    USER_CREATION,
    PASSWORD_CHANGE,
    PASSWORD_RESET_BY_USER,
    ROLES_ASSIGNMENT,
    ROLES_REMOVAL,
    PERMISSIONS_ASSIGNED_TO_USER,
    PERMISSIONS_REMOVED_FROM_USER,
    USER_DELETE,

    // Role Management
    ROLE_CREATION,
    ROLE_UPDATE,
    ROLE_DELETE,
    PERMISSIONS_ASSIGNED_TO_ROLE,
    PERMISSIONS_REMOVED_FROM_ROLE,

    // Password Reset
    PASSWORD_RESET_OTP_REQUEST,
    PASSWORD_RESET
}