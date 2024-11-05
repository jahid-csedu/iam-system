package com.example.iamsystem.permission;

import com.example.iamsystem.util.authorization.RequirePermission;
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

import static com.example.iamsystem.permission.PermissionAction.DELETE;
import static com.example.iamsystem.permission.PermissionAction.UPDATE;
import static com.example.iamsystem.permission.PermissionAction.WRITE;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<PermissionDto>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @PostMapping
    @RequirePermission(serviceName = "IAM", action = WRITE)
    public ResponseEntity<PermissionDto> createPermission(@RequestBody @Valid PermissionDto permissionDto) {

        return ResponseEntity.ok(permissionService.savePermission(permissionDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermissionDto> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @PutMapping("/{id}")
    @RequirePermission(serviceName = "IAM", action = UPDATE)
    public ResponseEntity<PermissionDto> updatePermission(@PathVariable Long id, @RequestBody @Valid PermissionDto permissionDto) {
        return ResponseEntity.ok(permissionService.updatePermission(id, permissionDto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = "IAM", action = DELETE)
    public ResponseEntity<Void> deletePermissionById(@PathVariable Long id) {
        permissionService.deletePermissionById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{serviceName}")
    public ResponseEntity<List<PermissionDto>> getPermissionByServiceName(@PathVariable String serviceName) {
        return ResponseEntity.ok(permissionService.getPermissionByServiceName(serviceName));
    }
}
