package com.example.iamsystem.role;

import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RolePermissionDto;
import com.example.iamsystem.util.authorization.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import static com.example.iamsystem.constant.PermissionConstants.IAM_SERVICE_NAME;
import static com.example.iamsystem.permission.model.PermissionAction.DELETE;
import static com.example.iamsystem.permission.model.PermissionAction.READ;
import static com.example.iamsystem.permission.model.PermissionAction.UPDATE;
import static com.example.iamsystem.permission.model.PermissionAction.WRITE;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get all roles (Requires: IAM:READ)")
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        return ResponseEntity.ok(roleService.getRoles());
    }

    @PostMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = WRITE)
    @Operation(summary = "Create a new role (Requires: IAM:WRITE)")
    public ResponseEntity<RoleDto> createRole(@RequestBody @Valid RoleDto roleDto) {
        return ResponseEntity.ok(roleService.createRole(roleDto));
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a role by ID (Requires: IAM:READ)")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @PutMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Update a role (Requires: IAM:UPDATE)")
    public ResponseEntity<RoleDto> updateRole(@PathVariable Long id, @RequestBody @Valid RoleDto roleDto) {
        return ResponseEntity.ok(roleService.updateRole(id, roleDto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Delete a role by ID (Requires: IAM:DELETE)")
    public ResponseEntity<Void> deleteRoleById(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{name}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a role by name (Requires: IAM:READ)")
    public ResponseEntity<RoleDto> getRoleByName(@PathVariable String name) {
        return ResponseEntity.ok(roleService.getRoleByName(name));
    }

    @PutMapping("/permissions")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Assign permissions to a role (Requires: IAM:UPDATE)")
    public ResponseEntity<Void> assignPermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        roleService.assignPermissions(rolePermissionDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permissions")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Remove permissions from a role (Requires: IAM:DELETE)")
    public ResponseEntity<Void> removePermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        roleService.removePermissions(rolePermissionDto);
        return ResponseEntity.noContent().build();
    }
}
