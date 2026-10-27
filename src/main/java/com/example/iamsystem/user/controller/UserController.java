package com.example.iamsystem.user.controller;

import com.example.iamsystem.authorization.RequirePermission;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserDto;
import com.example.iamsystem.user.dto.UserPermissionAttachmentDto;
import com.example.iamsystem.user.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.dto.UserUpdateDto;
import com.example.iamsystem.user.service.UserCreationService;
import com.example.iamsystem.user.service.UserPasswordService;
import com.example.iamsystem.user.service.UserPermissionService;
import com.example.iamsystem.user.service.UserProfileService;
import com.example.iamsystem.user.service.UserRoleService;
import com.example.iamsystem.user.service.UserService;
import com.example.iamsystem.user.service.UserStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
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

import static com.example.iamsystem.permission.PermissionKey.USERS_ACTIVATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_CREATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_DEACTIVATE;
import static com.example.iamsystem.permission.PermissionKey.USERS_DELETE;
import static com.example.iamsystem.permission.PermissionKey.USERS_READ;
import static com.example.iamsystem.permission.PermissionKey.USERS_RESET_PASSWORD;
import static com.example.iamsystem.permission.PermissionKey.USERS_UNLOCK;
import static com.example.iamsystem.permission.PermissionKey.USERS_UPDATE;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final UserCreationService userCreationService;
    private final UserPasswordService userPasswordService;
    private final UserRoleService userRoleService;
    private final UserPermissionService userPermissionService;
    private final UserService userService;
    private final UserProfileService userProfileService;
    private final UserStatusService userStatusService;

    @PostMapping
    @RequirePermission(USERS_CREATE)
    @Operation(summary = "Create a user in the caller's tenant; REGULAR unless userType is MANAGEMENT (Requires: users:create, and users:create-management for a management user)")
    public ResponseEntity<UserDto> createUser(@RequestBody @Valid UserCreationDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userCreationService.createUser(request));
    }

    @PatchMapping("/password")
    @Operation(summary = "Change your own password (old password required)")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid PasswordChangeDto request) {
        userPasswordService.changeOwnPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/password-reset")
    @RequirePermission(USERS_RESET_PASSWORD)
    @Operation(summary = "Reset another user's password: emails them a reset OTP and disables their current password (Requires: users:reset-password)")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        userPasswordService.resetPassword(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/roles")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Assign roles of your tenant to another management user (Requires: users:update)")
    public ResponseEntity<Void> assignRoles(@RequestBody @Valid UserRoleAttachmentDto request) {
        userRoleService.assignRoles(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Remove roles from another user (Requires: users:update)")
    public ResponseEntity<Void> removeRoles(@RequestBody @Valid UserRoleAttachmentDto request) {
        userRoleService.removeRoles(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/permissions")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Grant permissions directly to another management user (Requires: users:update)")
    public ResponseEntity<Void> assignPermissions(@RequestBody @Valid UserPermissionAttachmentDto request) {
        userPermissionService.assignPermissions(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/permissions")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Take directly granted permissions from another user (Requires: users:update)")
    public ResponseEntity<Void> removePermissions(@RequestBody @Valid UserPermissionAttachmentDto request) {
        userPermissionService.removePermissions(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @RequirePermission(USERS_READ)
    @Operation(summary = "List your tenant's users, one page at a time, ordered by id (Requires: users:read)")
    public ResponseEntity<PagedModel<UserDto>> getUsers(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) int size) {
        return ResponseEntity.ok(new PagedModel<>(userService.findUsers(page, size)));
    }

    @GetMapping("/{id}")
    @RequirePermission(USERS_READ)
    @Operation(summary = "Get a user by ID (Requires: users:read)")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @GetMapping("/by-username")
    @RequirePermission(USERS_READ)
    @Operation(summary = "Get a user by username (Requires: users:read)")
    public ResponseEntity<UserDto> getUserByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.findUserByUsername(username));
    }

    @GetMapping("/by-email")
    @RequirePermission(USERS_READ)
    @Operation(summary = "Get a user by email (Requires: users:read)")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        return ResponseEntity.ok(userService.findUserByEmail(email));
    }

    @PatchMapping("/{id}")
    @RequirePermission(USERS_UPDATE)
    @Operation(summary = "Change another user's full name and/or email; fields left out stay unchanged. A new email also needs users:reset-password. Never yourself (use /api/me), never the root user (Requires: users:update)")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @RequestBody @Valid UserUpdateDto request) {
        return ResponseEntity.ok(userProfileService.updateUser(id, request));
    }

    @PutMapping("/{id}/deactivate")
    @RequirePermission(USERS_DEACTIVATE)
    @Operation(summary = "Deactivate another user: no logins, and their tokens stop working until activated; sessions are kept. Never yourself, never the root user (Requires: users:deactivate)")
    public ResponseEntity<UserDto> deactivateUser(@PathVariable Long id) {
        return ResponseEntity.ok(userStatusService.deactivateUser(id));
    }

    @PutMapping("/{id}/activate")
    @RequirePermission(USERS_ACTIVATE)
    @Operation(summary = "Reactivate a deactivated user; their unexpired sessions work again (Requires: users:activate)")
    public ResponseEntity<UserDto> activateUser(@PathVariable Long id) {
        return ResponseEntity.ok(userStatusService.activateUser(id));
    }

    @PutMapping("/{id}/unlock")
    @RequirePermission(USERS_UNLOCK)
    @Operation(summary = "End another user's login lockout at once (wrong passwords counted so far are forgotten). Never yourself, never the root user (Requires: users:unlock)")
    public ResponseEntity<UserDto> unlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(userStatusService.unlockUser(id));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(USERS_DELETE)
    @Operation(summary = "Delete a user by ID (Requires: users:delete)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
