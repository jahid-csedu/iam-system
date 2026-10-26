package com.example.iamsystem.user.controller;

import com.example.iamsystem.user.dto.MeDto;
import com.example.iamsystem.user.service.MeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class MeController {

    private final MeService meService;

    @GetMapping
    @Operation(summary = "The logged-in management user: profile, home and working tenant, and every permission key held in the working tenant (management users only; no permission needed)")
    public ResponseEntity<MeDto> currentUser() {
        return ResponseEntity.ok(meService.currentUser());
    }
}
