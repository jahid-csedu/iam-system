package com.example.iamsystem.role;

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

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        return ResponseEntity.ok(roleService.getRoles());
    }

    @PostMapping
    public ResponseEntity<RoleDto> createRole(@RequestBody RoleDto roleDto) {
        return ResponseEntity.ok(roleService.createRole(roleDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto> updateRole(@PathVariable Long id, @RequestBody RoleDto roleDto) {
        return ResponseEntity.ok(roleService.updateRole(id, roleDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoleById(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<RoleDto> getRoleByName(@PathVariable String name) {
        return ResponseEntity.ok(roleService.getRoleByName(name));
    }

    @PutMapping("/permissions")
    public ResponseEntity<Void> assignPermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        roleService.assignPermissions(rolePermissionDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permissions")
    public ResponseEntity<Void> removePermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        roleService.removePermissions(rolePermissionDto);
        return ResponseEntity.noContent().build();
    }
}
