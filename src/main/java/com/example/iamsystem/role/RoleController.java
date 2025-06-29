package com.example.iamsystem.role;

import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RolePermissionDto;
import com.example.iamsystem.util.authorization.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get all roles (Requires: IAM:READ)")
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        log.debug("Received request to get all roles");
        List<RoleDto> roles = roleService.getRoles();
        log.info("Successfully retrieved {} roles", roles.size());
        return ResponseEntity.ok(roles);
    }

    @PostMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = WRITE)
    @Operation(summary = "Create a new role (Requires: IAM:WRITE)")
    public ResponseEntity<RoleDto> createRole(@RequestBody @Valid RoleDto roleDto) {
        log.debug("Received request to create role: {}", roleDto.getName());
        RoleDto createdRole = roleService.createRole(roleDto);
        log.info("Successfully created role with ID: {}", createdRole.getId());
        return ResponseEntity.ok(createdRole);
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a role by ID (Requires: IAM:READ)")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        log.debug("Received request to get role by ID: {}", id);
        RoleDto role = roleService.getRole(id);
        log.info("Successfully retrieved role with ID: {}", id);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Update a role (Requires: IAM:UPDATE)")
    public ResponseEntity<RoleDto> updateRole(@PathVariable Long id, @RequestBody @Valid RoleDto roleDto) {
        log.debug("Received request to update role with ID: {}", id);
        RoleDto updatedRole = roleService.updateRole(id, roleDto);
        log.info("Successfully updated role with ID: {}", id);
        return ResponseEntity.ok(updatedRole);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Delete a role by ID (Requires: IAM:DELETE)")
    public ResponseEntity<Void> deleteRoleById(@PathVariable Long id) {
        log.debug("Received request to delete role with ID: {}", id);
        roleService.deleteRole(id);
        log.info("Successfully deleted role with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{name}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a role by name (Requires: IAM:READ)")
    public ResponseEntity<RoleDto> getRoleByName(@PathVariable String name) {
        log.debug("Received request to get role by name: {}", name);
        RoleDto role = roleService.getRoleByName(name);
        log.info("Successfully retrieved role with name: {}", name);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/permissions")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Assign permissions to a role (Requires: IAM:UPDATE)")
    public ResponseEntity<Void> assignPermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        log.debug("Received request to assign permissions to role ID: {}", rolePermissionDto.getRoleId());
        roleService.assignPermissions(rolePermissionDto);
        log.info("Successfully assigned permissions to role ID: {}", rolePermissionDto.getRoleId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permissions")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Remove permissions from a role (Requires: IAM:DELETE)")
    public ResponseEntity<Void> removePermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        log.debug("Received request to remove permissions from role ID: {}", rolePermissionDto.getRoleId());
        roleService.removePermissions(rolePermissionDto);
        log.info("Successfully removed permissions from role ID: {}", rolePermissionDto.getRoleId());
        return ResponseEntity.noContent().build();
    }
}
