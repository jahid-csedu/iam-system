package com.example.management.user;

import com.example.management.constant.ErrorMessage;
import com.example.management.dto.JwtRefreshTokenDto;
import com.example.management.dto.JwtResponse;
import com.example.management.exception.JwtException;
import com.example.management.security.jwt.JwtTokenUtil;
import com.example.management.security.user.UserDetailsServiceImpl;
import com.example.management.user.model.dto.UserDto;
import com.example.management.user.model.dto.UserLoginDto;
import com.example.management.user.model.dto.UserRegistrationDto;
import com.example.management.user.model.dto.UserRoleAttachmentDto;
import com.example.management.user.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
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

import static com.example.management.constant.TokenType.ACCESS_TOKEN;
import static com.example.management.constant.TokenType.REFRESH_TOKEN;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {


    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;

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

    @PostMapping("/authenticate")
    public ResponseEntity<JwtResponse> createAuthenticationToken(@Valid @RequestBody UserLoginDto userLoginDto) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userLoginDto.getUsername(), userLoginDto.getPassword()));
        var userDetails = userDetailsService.loadUserByUsername(userLoginDto.getUsername());
        return new ResponseEntity<>(getTokens(userDetails, userLoginDto.getUsername()), HttpStatus.OK);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody JwtRefreshTokenDto refreshTokenDto) {
        String username = refreshTokenDto.getUsername();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = refreshTokenDto.getRefreshToken();
        if (jwtTokenUtil.validateToken(token, userDetails, REFRESH_TOKEN)) {
            return new ResponseEntity<>(getTokens(userDetails, refreshTokenDto.getUsername()), HttpStatus.OK);
        }
        throw new JwtException(ErrorMessage.INVALID_TOKEN);
    }

    private JwtResponse getTokens(UserDetails userDetails, String username) {
        String accessToken = jwtTokenUtil.generateToken(userDetails, ACCESS_TOKEN);
        String refreshToken = jwtTokenUtil.generateToken(userDetails, REFRESH_TOKEN);
        return new JwtResponse(username, refreshToken, accessToken);
    }
}
