package com.example.iamsystem.passwordreset.controller;

import com.example.iamsystem.passwordreset.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.iamsystem.passwordreset.PasswordResetConstants.OTP_SENT_SUCCESS;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_SUCCESS;

@RestController
@RequestMapping("/api/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/reset-request")
    public ResponseEntity<String> requestPasswordReset(@RequestParam("email") String email) {
        passwordResetService.createPasswordResetOtpForUser(email);
        return ResponseEntity.ok(OTP_SENT_SUCCESS);
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetPassword(@RequestParam("otp") String otp, @RequestParam("email") String email) {
        passwordResetService.resetPassword(otp, email);
        return ResponseEntity.ok(PASSWORD_RESET_SUCCESS);
    }
}
