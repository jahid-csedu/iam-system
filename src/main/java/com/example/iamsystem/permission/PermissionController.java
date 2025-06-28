package com.example.iamsystem.permission;

import com.example.iamsystem.permission.model.PermissionDto;
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
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PermissionController {
    private final PermissionService permissionService;

    @GetMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get all permissions (Requires: IAM:READ)")
    public ResponseEntity<List<PermissionDto>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @PostMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = WRITE)
    @Operation(summary = "Create a new permission (Requires: IAM:WRITE)")
    public ResponseEntity<PermissionDto> createPermission(@RequestBody @Valid PermissionDto permissionDto) {

        return ResponseEntity.ok(permissionService.savePermission(permissionDto));
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a permission by ID (Requires: IAM:READ)")
    public ResponseEntity<PermissionDto> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @PutMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Update a permission (Requires: IAM:UPDATE)")
    public ResponseEntity<PermissionDto> updatePermission(@PathVariable Long id, @RequestBody @Valid PermissionDto permissionDto) {
        return ResponseEntity.ok(permissionService.updatePermission(id, permissionDto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Delete a permission by ID (Requires: IAM:DELETE)")
    public ResponseEntity<Void> deletePermissionById(@PathVariable Long id) {
        permissionService.deletePermissionById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{serviceName}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get permissions by service name (Requires: IAM:READ)")
    public ResponseEntity<List<PermissionDto>> getPermissionByServiceName(@PathVariable String serviceName) {
        return ResponseEntity.ok(permissionService.getPermissionByServiceName(serviceName));
    }
}
