package com.example.iamsystem.auth.controller;

import com.example.iamsystem.auth.dto.AuthorizationRequest;
import com.example.iamsystem.auth.dto.AuthorizationResponse;
import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.auth.dto.TokenValidationRequest;
import com.example.iamsystem.auth.dto.TokenValidationResponse;
import com.example.iamsystem.auth.service.AuthenticationService;
import com.example.iamsystem.user.dto.UserLoginDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/authenticate")
    @Operation(summary = "User authentication")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody UserLoginDto request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "Refresh token")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody JwtRefreshTokenDto request) {
        return ResponseEntity.ok(authenticationService.refresh(request));
    }

    @PostMapping("/token/validate")
    @Operation(summary = "Token validation")
    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody TokenValidationRequest request) {
        return ResponseEntity.ok(authenticationService.validate(request));
    }

    @PostMapping("/authorize")
    @Operation(summary = "User authorization")
    public ResponseEntity<AuthorizationResponse> authorize(@Valid @RequestBody AuthorizationRequest request) {
        return ResponseEntity.ok(authenticationService.authorize(request));
    }
}
