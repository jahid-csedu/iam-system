package com.example.iamsystem.platform.controller;

import com.example.iamsystem.platform.service.PlatformTenantService;
import com.example.iamsystem.tenant.dto.TenantDto;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.dto.TenantSignupResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant administration by super users. {@code PlatformTenantService} checks the PLATFORM permission on the tenant each
 * request concerns (it is in the path, so {@code @RequirePermission} can't check it); tenant users are always refused.
 */
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PlatformTenantController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final PlatformTenantService platformTenantService;

    @PostMapping
    @Operation(summary = "Create a tenant with its root user, predefined roles and management console, like signup (Requires: tenants:create on all tenants)")
    public ResponseEntity<TenantSignupResultDto> createTenant(@RequestBody @Valid TenantSignupDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformTenantService.createTenant(request));
    }

    @GetMapping
    @Operation(summary = "List the tenants you hold tenants:read on, one page at a time, ordered by id")
    public ResponseEntity<PagedModel<TenantDto>> getTenants(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) int size) {
        return ResponseEntity.ok(new PagedModel<>(platformTenantService.findTenants(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tenant by ID (Requires: tenants:read on it)")
    public ResponseEntity<TenantDto> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(platformTenantService.findTenant(id));
    }

    @PutMapping("/{id}/suspend")
    @Operation(summary = "Suspend a tenant: none of its users can log in or use their tokens; never the platform tenant (Requires: tenants:suspend on it)")
    public ResponseEntity<TenantDto> suspendTenant(@PathVariable Long id) {
        return ResponseEntity.ok(platformTenantService.suspendTenant(id));
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate a suspended tenant again (Requires: tenants:activate on it)")
    public ResponseEntity<TenantDto> activateTenant(@PathVariable Long id) {
        return ResponseEntity.ok(platformTenantService.activateTenant(id));
    }
}
