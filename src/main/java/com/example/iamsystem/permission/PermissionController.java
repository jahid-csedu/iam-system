package com.example.iamsystem.permission;

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
    public ResponseEntity<PermissionDto> createPermission(@RequestBody PermissionDto permissionDto) {
        return ResponseEntity.ok(permissionService.savePermission(permissionDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermissionDto> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermissionDto> updatePermission(@PathVariable Long id, @RequestBody PermissionDto permissionDto) {
        return ResponseEntity.ok(permissionService.updatePermission(id, permissionDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePermissionById(@PathVariable Long id) {
        permissionService.deletePermissionById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{serviceName}")
    public ResponseEntity<PermissionDto> getPermissionByServiceName(@PathVariable String serviceName) {
        PermissionDto permission = permissionService.getPermissionByServiceName(serviceName);
        return ResponseEntity.ok(permission);
    }
}
