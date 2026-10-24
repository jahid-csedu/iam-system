package com.example.iamsystem.tenant.controller;

import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.dto.TenantSignupResultDto;
import com.example.iamsystem.tenant.service.TenantSignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantSignupService tenantSignupService;

    @PostMapping("/signup")
    @Operation(summary = "Create a tenant and its root user")
    @SecurityRequirement(name = "")
    public ResponseEntity<TenantSignupResultDto> signup(@RequestBody @Valid TenantSignupDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantSignupService.signup(request));
    }
}
