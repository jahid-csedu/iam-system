package com.example.iamsystem.platform.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.platform.dto.PermissionGrantDto;
import com.example.iamsystem.platform.dto.PermissionGrantRequestDto;
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

import java.util.List;

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
    @Operation(summary = "Grant platform roles to a platform user on one tenant, or on all tenants without tenantId (Requires: users:update in the platform tenant)")
    public ResponseEntity<List<RoleGrantDto>> grantRoles(@RequestBody @Valid RoleGrantRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformGrantService.grantRoles(request));
    }

    @DeleteMapping("/grants/roles")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Remove role grants of a platform user on one tenant, or their all-tenants grants without tenantId; never your own (Requires: users:update in the platform tenant)")
    public ResponseEntity<Void> revokeRoles(@RequestBody @Valid RoleGrantRequestDto request) {
        platformGrantService.revokeRoles(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/grants/permissions")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Grant permissions to a platform user on one tenant, or on all tenants without tenantId (Requires: users:update in the platform tenant)")
    public ResponseEntity<List<PermissionGrantDto>> grantPermissions(@RequestBody @Valid PermissionGrantRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformGrantService.grantPermissions(request));
    }

    @DeleteMapping("/grants/permissions")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Remove permission grants of a platform user on one tenant, or their all-tenants grants without tenantId; never your own (Requires: users:update in the platform tenant)")
    public ResponseEntity<Void> revokePermissions(@RequestBody @Valid PermissionGrantRequestDto request) {
        platformGrantService.revokePermissions(request);
        return ResponseEntity.noContent().build();
    }
}
