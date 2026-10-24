package com.example.iamsystem.user.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.dto.UserRegistrationDto;
import com.example.iamsystem.user.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.service.UserPasswordService;
import com.example.iamsystem.user.service.UserRegistrationService;
import com.example.iamsystem.user.service.UserRoleService;
import com.example.iamsystem.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.iamsystem.permission.PermissionConstants.IAM_SERVICE_NAME;
import static com.example.iamsystem.permission.entity.PermissionAction.DELETE;
import static com.example.iamsystem.permission.entity.PermissionAction.READ;
import static com.example.iamsystem.permission.entity.PermissionAction.UPDATE;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserRegistrationService userRegistrationService;
    private final UserPasswordService userPasswordService;
    private final UserRoleService userRoleService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @SecurityRequirement(name = "")
    public ResponseEntity<UserDto> registerUser(@RequestBody @Valid UserRegistrationDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userRegistrationService.registerUser(request));
    }

    @PatchMapping("/password")
    @Operation(summary = "Change user password. Can be done by the user or a root user for each subordinates")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid PasswordChangeDto request,
                                               @RequestParam(required = false) String username) {
        userPasswordService.changePassword(request, username);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/roles")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Assign roles to a user (Requires: IAM:UPDATE)")
    public ResponseEntity<Void> assignRoles(@RequestBody @Valid UserRoleAttachmentDto request) {
        userRoleService.assignRoles(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Remove roles from a user (Requires: IAM:UPDATE)")
    public ResponseEntity<Void> removeRoles(@RequestBody @Valid UserRoleAttachmentDto request) {
        userRoleService.removeRoles(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get all users (Requires: IAM:READ)")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a user by ID (Requires: IAM:READ)")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @GetMapping("/by-username")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a user by username (Requires: IAM:READ)")
    public ResponseEntity<UserDto> getUserByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.findUserByUsername(username));
    }

    @GetMapping("/by-email")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a user by email (Requires: IAM:READ)")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        return ResponseEntity.ok(userService.findUserByEmail(email));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Delete a user by ID (Requires: IAM:DELETE)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
