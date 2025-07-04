package com.example.iamsystem.user;

import com.example.iamsystem.user.model.dto.PasswordChangeDto;
import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.dto.UserRoleAttachmentDto;
import com.example.iamsystem.util.authorization.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;

import static com.example.iamsystem.constant.PermissionConstants.IAM_SERVICE_NAME;
import static com.example.iamsystem.permission.model.PermissionAction.DELETE;
import static com.example.iamsystem.permission.model.PermissionAction.READ;
import static com.example.iamsystem.permission.model.PermissionAction.UPDATE;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @SecurityRequirement(name = "")
    public ResponseEntity<UserDto> userRegistration(@RequestBody @Valid UserRegistrationDto userDto) {
        log.debug("Received request to register user with username: {}", userDto.getUsername());
        UserDto registeredUserDto = userService.registerUser(userDto);
        log.info("User registered successfully with ID: {}", registeredUserDto.getId());
        return new ResponseEntity<>(registeredUserDto, HttpStatus.CREATED);
    }

    @PatchMapping("/password")
    @Operation(summary = "Change user password. Can be done by the user or a root user for each subordinates")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid PasswordChangeDto passwordChangeDto,
                                               @RequestParam(required = false) String username) {
        log.debug("Received request to change password");
        userService.changePassword(passwordChangeDto, username);
        log.info("Password changed successfully");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/roles")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = UPDATE)
    @Operation(summary = "Assign roles to a user (Requires: IAM:UPDATE)")
    public ResponseEntity<Void> assignRoles(@RequestBody @Valid UserRoleAttachmentDto userRoleAttachmentDto) {
        log.debug("Received request to assign roles to user: {}", userRoleAttachmentDto.getUsername());
        userService.assignRoles(userRoleAttachmentDto);
        log.info("Roles assigned successfully to user: {}", userRoleAttachmentDto.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Remove roles from a user (Requires: IAM:DELETE)")
    public ResponseEntity<Void> removeRoles(@RequestBody @Valid UserRoleAttachmentDto userRoleAttachmentDto) {
        log.debug("Received request to remove roles from user: {}", userRoleAttachmentDto.getUsername());
        userService.removeRoles(userRoleAttachmentDto);
        log.info("Roles removed successfully from user: {}", userRoleAttachmentDto.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get all users (Requires: IAM:READ)")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        log.debug("Received request to get all users");
        List<UserDto> users = userService.findAllUsers();
        log.info("Successfully retrieved {} users", users.size());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a user by ID (Requires: IAM:READ)")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        log.debug("Received request to get user by ID: {}", id);
        UserDto user = userService.findUserById(id);
        log.info("Successfully retrieved user with ID: {}", id);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/by-username")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a user by username (Requires: IAM:READ)")
    public ResponseEntity<UserDto> getUserByUsername(@RequestParam String username) {
        log.debug("Received request to get user by username: {}", username);
        UserDto user = userService.findUserByUsername(username);
        log.info("Successfully retrieved user with username: {}", username);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/by-email")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = READ)
    @Operation(summary = "Get a user by email (Requires: IAM:READ)")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        log.debug("Received request to get user by email: {}", email);
        UserDto user = userService.findUserByEmail(email);
        log.info("Successfully retrieved user with email: {}", email);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = IAM_SERVICE_NAME, action = DELETE)
    @Operation(summary = "Delete a user by ID (Requires: IAM:DELETE)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.debug("Received request to delete user with ID: {}", id);
        userService.deleteUser(id);
        log.info("Successfully deleted user with ID: {}", id);
        return ResponseEntity.noContent().build();
    }
}
