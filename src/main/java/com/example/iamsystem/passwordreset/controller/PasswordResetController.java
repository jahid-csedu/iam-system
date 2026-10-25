package com.example.iamsystem.passwordreset.controller;

import com.example.iamsystem.passwordreset.dto.PasswordResetDto;
import com.example.iamsystem.passwordreset.dto.PasswordResetRequestDto;
import com.example.iamsystem.passwordreset.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.iamsystem.passwordreset.PasswordResetConstants.PASSWORD_RESET_SUCCESS;
import static com.example.iamsystem.passwordreset.PasswordResetConstants.RESET_REQUEST_ACCEPTED;

/**
 * Self-service password reset for users who are not logged in: the OTP goes only to the user's email, and the user then
 * sets a new password with it.
 */
@RestController
@RequestMapping("/api/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/reset-request")
    @Operation(summary = "Email a password-reset OTP to the user with this username or email in the client's tenant; the answer is the same whether or not the user exists (public)")
    public ResponseEntity<String> requestPasswordReset(@RequestBody @Valid PasswordResetRequestDto request) {
        passwordResetService.createPasswordResetOtp(request.clientId(), request.identifier());
        return ResponseEntity.ok(RESET_REQUEST_ACCEPTED);
    }

    @PostMapping("/reset")
    @Operation(summary = "With a valid OTP, set the new password the user chose; every session of the user ends (public)")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid PasswordResetDto request) {
        passwordResetService.resetPassword(request.clientId(), request.identifier(), request.otp(), request.newPassword());
        return ResponseEntity.ok(PASSWORD_RESET_SUCCESS);
    }
}
