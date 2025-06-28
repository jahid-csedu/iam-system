package com.example.iamsystem.security.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.iamsystem.constant.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.constant.TokenType.REFRESH_TOKEN;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final DefaultUserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final PermissionService permissionService;


    @PostMapping("/authenticate")
    @Operation(summary = "User authentication")
    public ResponseEntity<JwtResponse> createAuthenticationToken(@Valid @RequestBody UserLoginDto userLoginDto) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userLoginDto.getUsername(), userLoginDto.getPassword()));
        var userDetails = userDetailsService.loadUserByUsername(userLoginDto.getUsername());
        return new ResponseEntity<>(getTokens(userDetails, userLoginDto.getUsername()), HttpStatus.OK);
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "Refresh token")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody JwtRefreshTokenDto refreshTokenDto) {
        String username = refreshTokenDto.getUsername();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = refreshTokenDto.getRefreshToken();
        if (jwtTokenUtil.validateToken(token, userDetails, REFRESH_TOKEN)) {
            return new ResponseEntity<>(getTokens(userDetails, refreshTokenDto.getUsername()), HttpStatus.OK);
        }
        throw new JwtException(ErrorMessage.INVALID_TOKEN);
    }

    @PostMapping("/token/validate")
    @Operation(summary = "Token validation")
    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody TokenValidationRequest request) {
        boolean valid = jwtTokenUtil.validateToken(request.getToken(), ACCESS_TOKEN);
        return ResponseEntity.ok(new TokenValidationResponse(valid));
    }

    @PostMapping("/authorize")
    @Operation(summary = "User authorization")
    public ResponseEntity<AuthorizationResponse> authorize(@Valid @RequestBody AuthorizationRequest authorizationRequest) {
        String requiredPermission = authorizationRequest.getServiceName() + ":" + authorizationRequest.getAction();
        boolean permission = permissionService.hasPermission(requiredPermission);
        return ResponseEntity.ok(new AuthorizationResponse(permission));
    }

    private JwtResponse getTokens(UserDetails userDetails, String username) {
        String accessToken = jwtTokenUtil.generateToken(userDetails, ACCESS_TOKEN);
        String refreshToken = jwtTokenUtil.generateToken(userDetails, REFRESH_TOKEN);
        return new JwtResponse(username, refreshToken, accessToken);
    }
}
