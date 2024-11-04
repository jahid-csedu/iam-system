package com.example.iamsystem.user;

import com.example.iamsystem.user.model.dto.UserDto;
import com.example.iamsystem.user.model.dto.UserRegistrationDto;
import com.example.iamsystem.user.model.dto.UserRoleAttachmentDto;
import com.example.iamsystem.user.model.entity.User;
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

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationDto> userRegistration(@Valid @RequestBody UserRegistrationDto userDto) {
        User registeresUser = userService.registerUser(userDto);
        userDto.setPassword(null);
        if (registeresUser.getId() != null) {
            return new ResponseEntity<>(userDto, HttpStatus.OK);
        }
        return new ResponseEntity<>(userDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PutMapping("/roles")
    public ResponseEntity<Void> assignRoles(@RequestBody @Valid UserRoleAttachmentDto userRoleAttachmentDto) {
        userService.assignRoles(userRoleAttachmentDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles")
    public ResponseEntity<Void> removeRoles(@RequestBody @Valid UserRoleAttachmentDto userRoleAttachmentDto) {
        userService.removeRoles(userRoleAttachmentDto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @GetMapping("/by-username")
    public ResponseEntity<UserDto> getUserByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.findUserByUsername(username));
    }

    @GetMapping("/by-email")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        return ResponseEntity.ok(userService.findUserByEmail(email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
