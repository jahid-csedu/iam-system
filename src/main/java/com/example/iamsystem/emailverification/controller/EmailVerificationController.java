package com.example.iamsystem.emailverification.controller;

import com.example.iamsystem.emailverification.dto.EmailVerificationConfirmDto;
import com.example.iamsystem.emailverification.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/email-verification")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping
    @Operation(summary = "Email a verification code to your own address (any logged-in user; 3 per hour)")
    public ResponseEntity<Void> requestCode() {
        emailVerificationService.requestCode();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    @Operation(summary = "Verify your own email address with the emailed code (any logged-in user)")
    public ResponseEntity<Void> confirm(@RequestBody @Valid EmailVerificationConfirmDto request) {
        emailVerificationService.confirm(request.code());
        return ResponseEntity.noContent().build();
    }
}
