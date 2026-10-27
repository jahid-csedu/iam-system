package com.example.iamsystem.email.dto;

/**
 * Why an email is sent. Each purpose has a subject and a template pair, {@code email/<template>.html} and
 * {@code email/<template>.txt}, which name the values they need.
 */
public enum EmailPurpose {

    /**
     * Values: {@code otp} (the six digits), {@code validMinutes}.
     */
    PASSWORD_RESET_OTP("Your password reset code", "password-reset-otp");

    private final String subject;
    private final String template;

    EmailPurpose(String subject, String template) {
        this.subject = subject;
        this.template = template;
    }

    public String subject() {
        return subject;
    }

    public String htmlTemplate() {
        return template + ".html";
    }

    public String textTemplate() {
        return template + ".txt";
    }
}
