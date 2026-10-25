package com.example.iamsystem.auth.controller;

import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.auth.dto.IntrospectionRequest;
import com.example.iamsystem.auth.dto.IntrospectionResponse;
import com.example.iamsystem.auth.service.AuthenticationService;
import com.example.iamsystem.auth.service.TokenIntrospectionService;
import com.example.iamsystem.user.dto.UserLoginDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    private final TokenIntrospectionService introspectionService;

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

    @PostMapping("/logout")
    @Operation(summary = "Log out: end the session of the Bearer access token")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Void> logout() {
        authenticationService.logout();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/introspect")
    @Operation(summary = "Is this access token active right now? (RFC 7662 style; always 200)")
    public ResponseEntity<IntrospectionResponse> introspect(@Valid @RequestBody IntrospectionRequest request) {
        return ResponseEntity.ok(introspectionService.introspect(request));
    }
}
