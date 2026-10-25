package com.example.iamsystem.platform.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.platform.service.PlatformTenantService;
import com.example.iamsystem.tenant.dto.TenantDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.iamsystem.permission.PermissionKey.TENANTS_ACTIVATE;
import static com.example.iamsystem.permission.PermissionKey.TENANTS_READ;
import static com.example.iamsystem.permission.PermissionKey.TENANTS_SUSPEND;

/**
 * Tenant administration by super users. Every endpoint needs a PLATFORM permission, which tenant users never hold.
 */
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PlatformTenantController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final PlatformTenantService platformTenantService;

    @GetMapping
    @RequirePermission(TENANTS_READ)
    @Operation(summary = "List all tenants, one page at a time, ordered by id (Requires: tenants:read)")
    public ResponseEntity<PagedModel<TenantDto>> getTenants(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(new PagedModel<>(platformTenantService.findTenants(page, size)));
    }

    @GetMapping("/{id}")
    @RequirePermission(TENANTS_READ)
    @Operation(summary = "Get a tenant by ID (Requires: tenants:read)")
    public ResponseEntity<TenantDto> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(platformTenantService.findTenant(id));
    }

    @PutMapping("/{id}/suspend")
    @RequirePermission(TENANTS_SUSPEND)
    @Operation(summary = "Suspend a tenant: none of its users can log in or use their tokens; never the platform tenant (Requires: tenants:suspend)")
    public ResponseEntity<TenantDto> suspendTenant(@PathVariable Long id) {
        return ResponseEntity.ok(platformTenantService.suspendTenant(id));
    }

    @PutMapping("/{id}/activate")
    @RequirePermission(TENANTS_ACTIVATE)
    @Operation(summary = "Activate a suspended tenant again (Requires: tenants:activate)")
    public ResponseEntity<TenantDto> activateTenant(@PathVariable Long id) {
        return ResponseEntity.ok(platformTenantService.activateTenant(id));
    }
}
