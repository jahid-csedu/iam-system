package com.example.iamsystem.passwordreset;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordResetConstants {

    // Email Subjects
    public static final String PASSWORD_RESET_REQUEST_SUBJECT = "Password Reset Request";
    public static final String PASSWORD_RESET_SUCCESS_SUBJECT = "Password Reset";

    // Email Bodies
    public static final String PASSWORD_RESET_REQUEST_BODY_PREFIX = "To reset your password, use the following OTP: ";
    public static final String PASSWORD_RESET_SUCCESS_BODY_PREFIX = "Your new password is: ";

    // Error Messages
    public static final String INVALID_OTP = "Invalid or expired OTP";

    // Controller Response Messages
    public static final String RESET_REQUEST_ACCEPTED =
            "If the account exists and has an email address, a password reset OTP has been sent to it.";
    public static final String PASSWORD_RESET_SUCCESS = "Your new password has been sent to your email.";
}
