package com.example.iamsystem.permission.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.permission.dto.PermissionDto;
import com.example.iamsystem.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.iamsystem.permission.PermissionKey.PERMISSIONS_READ;

/**
 * The permission catalog is read-only: permissions are added by migrations together with the features they protect.
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @RequirePermission(PERMISSIONS_READ)
    @Operation(summary = "List the permission catalog (Requires: permissions:read)")
    public ResponseEntity<List<PermissionDto>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @GetMapping("/{id}")
    @RequirePermission(PERMISSIONS_READ)
    @Operation(summary = "Get a permission by ID (Requires: permissions:read)")
    public ResponseEntity<PermissionDto> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }
}
