package com.example.iamsystem.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PasswordResetConstants {

    // Email Subjects
    public static final String PASSWORD_RESET_REQUEST_SUBJECT = "Password Reset Request";
    public static final String PASSWORD_RESET_SUCCESS_SUBJECT = "Password Reset";

    // Email Bodies
    public static final String PASSWORD_RESET_REQUEST_BODY_PREFIX = "To reset your password, use the following OTP: ";
    public static final String PASSWORD_RESET_SUCCESS_BODY_PREFIX = "Your new password is: ";

    // Error Messages
    public static final String USER_NOT_FOUND = "User not found";
    public static final String INVALID_OTP = "Invalid OTP";
    public static final String OTP_DOES_NOT_BELONG_TO_USER = "OTP does not belong to this user";
    public static final String OTP_HAS_EXPIRED = "OTP has expired";

    // Controller Response Messages
    public static final String OTP_SENT_SUCCESS = "Password reset OTP sent to your email.";
    public static final String PASSWORD_RESET_SUCCESS = "Your new password has been sent to your email.";
}
