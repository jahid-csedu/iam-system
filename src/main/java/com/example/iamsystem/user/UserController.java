package com.example.iamsystem.user;

import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.dto.UserRoleAttachmentDto;
import com.example.iamsystem.util.authorization.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.iamsystem.permission.model.PermissionAction.DELETE;
import static com.example.iamsystem.permission.model.PermissionAction.READ;
import static com.example.iamsystem.permission.model.PermissionAction.UPDATE;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserDto> userRegistration(@RequestBody @Valid UserRegistrationDto userDto) {
        UserDto registeredUserDto = userService.registerUser(userDto);
        return new ResponseEntity<>(registeredUserDto, HttpStatus.CREATED);
    }

    @PutMapping("/roles")
    @RequirePermission(serviceName = "IAM", action = UPDATE)
    public ResponseEntity<Void> assignRoles(@RequestBody @Valid UserRoleAttachmentDto userRoleAttachmentDto) {
        userService.assignRoles(userRoleAttachmentDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles")
    @RequirePermission(serviceName = "IAM", action = DELETE)
    public ResponseEntity<Void> removeRoles(@RequestBody @Valid UserRoleAttachmentDto userRoleAttachmentDto) {
        userService.removeRoles(userRoleAttachmentDto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @RequirePermission(serviceName = "IAM", action = READ)
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @GetMapping("/{id}")
    @RequirePermission(serviceName = "IAM", action = READ)
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @GetMapping("/by-username")
    @RequirePermission(serviceName = "IAM", action = READ)
    public ResponseEntity<UserDto> getUserByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.findUserByUsername(username));
    }

    @GetMapping("/by-email")
    @RequirePermission(serviceName = "IAM", action = READ)
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        return ResponseEntity.ok(userService.findUserByEmail(email));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(serviceName = "IAM", action = DELETE)
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
