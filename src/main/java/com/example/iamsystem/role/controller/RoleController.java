package com.example.iamsystem.role.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.role.dto.RoleDto;
import com.example.iamsystem.role.dto.RolePermissionDto;
import com.example.iamsystem.role.dto.RoleRequestDto;
import com.example.iamsystem.role.service.RolePermissionService;
import com.example.iamsystem.role.service.RoleService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.iamsystem.permission.PermissionKey.ROLES_CREATE;
import static com.example.iamsystem.permission.PermissionKey.ROLES_DELETE;
import static com.example.iamsystem.permission.PermissionKey.ROLES_READ;
import static com.example.iamsystem.permission.PermissionKey.ROLES_UPDATE;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    @GetMapping
    @RequirePermission(ROLES_READ)
    @Operation(summary = "List your tenant's roles (Requires: roles:read)")
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        return ResponseEntity.ok(roleService.getRoles());
    }

    @PostMapping
    @RequirePermission(ROLES_CREATE)
    @Operation(summary = "Create a role in your tenant (Requires: roles:create)")
    public ResponseEntity<RoleDto> createRole(@RequestBody @Valid RoleRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request));
    }

    @GetMapping("/{id}")
    @RequirePermission(ROLES_READ)
    @Operation(summary = "Get a role by ID (Requires: roles:read)")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @GetMapping("/name/{name}")
    @RequirePermission(ROLES_READ)
    @Operation(summary = "Get a role by name (Requires: roles:read)")
    public ResponseEntity<RoleDto> getRoleByName(@PathVariable String name) {
        return ResponseEntity.ok(roleService.getRoleByName(name));
    }

    @PutMapping("/{id}")
    @RequirePermission(ROLES_UPDATE)
    @Operation(summary = "Update a role you don't hold (Requires: roles:update)")
    public ResponseEntity<RoleDto> updateRole(@PathVariable Long id, @RequestBody @Valid RoleRequestDto request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(ROLES_DELETE)
    @Operation(summary = "Delete a role you don't hold; it is removed from its users (Requires: roles:delete)")
    public ResponseEntity<Void> deleteRoleById(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/permissions")
    @RequirePermission(ROLES_UPDATE)
    @Operation(summary = "Assign permissions to a role you don't hold (Requires: roles:update)")
    public ResponseEntity<Void> assignPermissions(@RequestBody @Valid RolePermissionDto request) {
        rolePermissionService.assignPermissions(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permissions")
    @RequirePermission(ROLES_UPDATE)
    @Operation(summary = "Remove permissions from a role you don't hold (Requires: roles:update)")
    public ResponseEntity<Void> removePermissions(@RequestBody @Valid RolePermissionDto request) {
        rolePermissionService.removePermissions(request);
        return ResponseEntity.noContent().build();
    }
}
