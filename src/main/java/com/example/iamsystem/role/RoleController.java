package com.example.iamsystem.role;

import com.example.iamsystem.role.model.RoleDto;
import com.example.iamsystem.role.model.RolePermissionDto;
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

import static com.example.iamsystem.permission.model.PermissionAction.DELETE;
import static com.example.iamsystem.permission.model.PermissionAction.READ;
import static com.example.iamsystem.permission.model.PermissionAction.UPDATE;
import static com.example.iamsystem.permission.model.PermissionAction.WRITE;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    @RequirePermission(serviceName = "IAM", action = READ)
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        return ResponseEntity.ok(roleService.getRoles());
    }

    @PostMapping
    @RequirePermission(serviceName = "IAM", action = WRITE)
    public ResponseEntity<RoleDto> createRole(@RequestBody @Valid RoleDto roleDto) {
        return ResponseEntity.ok(roleService.createRole(roleDto));
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = "IAM", action = READ)
    public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @PutMapping("/{id}")
    @RequirePermission(serviceName = "IAM", action = UPDATE)
    public ResponseEntity<RoleDto> updateRole(@PathVariable Long id, @RequestBody @Valid RoleDto roleDto) {
        return ResponseEntity.ok(roleService.updateRole(id, roleDto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = "IAM", action = DELETE)
    public ResponseEntity<Void> deleteRoleById(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/name/{name}")
    @RequirePermission(serviceName = "IAM", action = READ)
    public ResponseEntity<RoleDto> getRoleByName(@PathVariable String name) {
        return ResponseEntity.ok(roleService.getRoleByName(name));
    }

    @PutMapping("/permissions")
    @RequirePermission(serviceName = "IAM", action = UPDATE)
    public ResponseEntity<Void> assignPermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        roleService.assignPermissions(rolePermissionDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permissions")
    @RequirePermission(serviceName = "IAM", action = DELETE)
    public ResponseEntity<Void> removePermissions(@RequestBody @Valid RolePermissionDto rolePermissionDto) {
        roleService.removePermissions(rolePermissionDto);
        return ResponseEntity.noContent().build();
    }
}
