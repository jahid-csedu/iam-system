package com.example.iamsystem.passwordreset;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordResetConstants {

    // Error Messages
    public static final String INVALID_OTP = "Invalid or expired OTP";

    // Controller Response Messages
    public static final String RESET_REQUEST_ACCEPTED =
            "If the account exists and has an email address, a password reset OTP has been sent to it.";
    public static final String PASSWORD_RESET_SUCCESS = "Your password has been reset. Log in with your new password.";
}
