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

import static com.example.iamsystem.permission.PermissionConstants.IAM_SERVICE_NAME;
import static com.example.iamsystem.permission.entity.PermissionAction.DELETE;
import static com.example.iamsystem.permission.entity.PermissionAction.READ;
import static com.example.iamsystem.permission.entity.PermissionAction.UPDATE;
import static com.example.iamsystem.permission.entity.PermissionAction.WRITE;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    @GetMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "List your tenant's roles (Requires: IAM:READ)")
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        return ResponseEntity.ok(roleService.getRoles());
    }

    @PostMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = WRITE)
    @Operation(summary = "Create a role in your tenant (Requires: IAM:WRITE)")
    public ResponseEntity<RoleDto> createRole(@RequestBody @Valid RoleRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request));
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a role by ID (Requires: IAM:READ)")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @GetMapping("/name/{name}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a role by name (Requires: IAM:READ)")
    public ResponseEntity<RoleDto> getRoleByName(@PathVariable String name) {
        return ResponseEntity.ok(roleService.getRoleByName(name));
    }

    @PutMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Update a role you don't hold (Requires: IAM:UPDATE)")
    public ResponseEntity<RoleDto> updateRole(@PathVariable Long id, @RequestBody @Valid RoleRequestDto request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Delete a role you don't hold; it is removed from its users (Requires: IAM:DELETE)")
    public ResponseEntity<Void> deleteRoleById(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/permissions")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Assign permissions to a role you don't hold (Requires: IAM:UPDATE)")
    public ResponseEntity<Void> assignPermissions(@RequestBody @Valid RolePermissionDto request) {
        rolePermissionService.assignPermissions(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permissions")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Remove permissions from a role you don't hold (Requires: IAM:UPDATE)")
    public ResponseEntity<Void> removePermissions(@RequestBody @Valid RolePermissionDto request) {
        rolePermissionService.removePermissions(request);
        return ResponseEntity.noContent().build();
    }
}
