package com.example.iamsystem.platform.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.platform.dto.PlatformGrantsDto;
import com.example.iamsystem.platform.dto.RoleGrantDto;
import com.example.iamsystem.platform.dto.RoleGrantRequestDto;
import com.example.iamsystem.platform.service.PlatformGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.iamsystem.permission.PermissionKey.USERS_READ;
import static com.example.iamsystem.permission.PermissionKey.USERS_UPDATE;

/**
 * Platform grants: what platform users may do inside other tenants. Managed from inside the platform tenant with the
 * same keys as roles ({@code users:read}, {@code users:update}).
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PlatformGrantController {

    private final PlatformGrantService platformGrantService;

    @GetMapping("/users/{userId}/grants")
    @RequirePermission(USERS_READ)
    @Operation(summary = "List a platform user's grants (Requires: users:read in the platform tenant)")
    public ResponseEntity<PlatformGrantsDto> getGrants(@PathVariable Long userId) {
        return ResponseEntity.ok(platformGrantService.findGrants(userId));
    }

    @PostMapping("/grants/roles")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Grant a platform role to a platform user on one tenant, or on all tenants without tenantId (Requires: users:update in the platform tenant)")
    public ResponseEntity<RoleGrantDto> grantRole(@RequestBody @Valid RoleGrantRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformGrantService.grantRole(request));
    }

    @DeleteMapping("/grants/roles/{id}")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Remove a role grant; never your own (Requires: users:update in the platform tenant)")
    public ResponseEntity<Void> revokeRoleGrant(@PathVariable Long id) {
        platformGrantService.revokeRoleGrant(id);
        return ResponseEntity.noContent().build();
    }
}
