package com.example.iamsystem.security.controller;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.constant.ErrorMessage;
import com.example.iamsystem.exception.JwtException;
import com.example.iamsystem.permission.PermissionService;
import com.example.iamsystem.security.dto.AuthorizationRequest;
import com.example.iamsystem.security.dto.AuthorizationResponse;
import com.example.iamsystem.security.dto.JwtRefreshTokenDto;
import com.example.iamsystem.security.dto.JwtResponse;
import com.example.iamsystem.security.dto.TokenValidationRequest;
import com.example.iamsystem.security.dto.TokenValidationResponse;
import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.security.user.DefaultUserDetailsService;
import com.example.iamsystem.user.model.dto.UserLoginDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.iamsystem.enums.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.enums.TokenType.REFRESH_TOKEN;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final DefaultUserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final PermissionService permissionService;

    @Auditable(
            value = AuditEventType.USER_LOGIN,
            target = "#userLoginDto.username"
    )
    @PostMapping("/authenticate")
    @Operation(summary = "User authentication")
    public ResponseEntity<JwtResponse> createAuthenticationToken(@Valid @RequestBody UserLoginDto userLoginDto) {
        log.debug("Authentication request received for user: {}", userLoginDto.getUsername());
        String username = userLoginDto.getUsername();

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginDto.getPassword()));
        var userDetails = userDetailsService.loadUserByUsername(username);
        log.info("User '{}' authenticated successfully.", username);

        return new ResponseEntity<>(getTokens(userDetails, username), HttpStatus.OK);
    }

    @Auditable(
            value = AuditEventType.TOKEN_REFRESH,
            target = "#refreshTokenDto.username"
    )
    @PostMapping("/token/refresh")
    @Operation(summary = "Refresh token")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody JwtRefreshTokenDto refreshTokenDto) {
        log.debug("Refresh token request received for user: {}", refreshTokenDto.getUsername());
        String username = refreshTokenDto.getUsername();

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = refreshTokenDto.getRefreshToken();
        if (jwtTokenUtil.validateToken(token, userDetails, REFRESH_TOKEN)) {
            log.info("Refresh token validated successfully for user: {}", username);
            return new ResponseEntity<>(getTokens(userDetails, refreshTokenDto.getUsername()), HttpStatus.OK);
        }
        log.warn("Invalid refresh token provided for user: {}", username);
        throw new JwtException(ErrorMessage.INVALID_TOKEN);
    }

    @PostMapping("/token/validate")
    @Operation(summary = "Token validation")
    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody TokenValidationRequest request) {
        log.debug("Token validation request received.");
        boolean valid = jwtTokenUtil.validateToken(request.getToken(), ACCESS_TOKEN);
        log.info("Token validation result: {}", valid);
        return ResponseEntity.ok(new TokenValidationResponse(valid));
    }

    @PostMapping("/authorize")
    @Operation(summary = "User authorization")
    public ResponseEntity<AuthorizationResponse> authorize(@Valid @RequestBody AuthorizationRequest authorizationRequest) {
        log.debug("Authorization request received for service: {} and action: {}", authorizationRequest.getServiceName(), authorizationRequest.getAction());
        String requiredPermission = authorizationRequest.getServiceName() + ":" + authorizationRequest.getAction();
        boolean permission = permissionService.hasPermission(requiredPermission);
        log.info("Authorization result for permission '{}': {}", requiredPermission, permission);
        return ResponseEntity.ok(new AuthorizationResponse(permission));
    }

    private JwtResponse getTokens(UserDetails userDetails, String username) {
        log.debug("Generating access and refresh tokens for user: {}", username);
        String accessToken = jwtTokenUtil.generateToken(userDetails, ACCESS_TOKEN);
        String refreshToken = jwtTokenUtil.generateToken(userDetails, REFRESH_TOKEN);
        log.debug("Tokens generated for user: {}", username);
        return new JwtResponse(username, refreshToken, accessToken);
    }
}
