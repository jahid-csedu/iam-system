package com.example.iamsystem.auth.controller;

import com.example.iamsystem.auth.dto.JwkSetDto;
import com.example.iamsystem.security.jwt.SigningKeys;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JwksController {

    public static final String JWKS_PATH = "/.well-known/jwks.json";

    private final SigningKeys signingKeys;

    @GetMapping(JWKS_PATH)
    @Operation(summary = "Public keys for verifying access tokens (JWKS)")
    public ResponseEntity<JwkSetDto> jwks() {
        return ResponseEntity.ok(JwkSetDto.of(signingKeys.all()));
    }
}
