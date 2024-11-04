package com.example.iamsystem.security.controller;

import com.example.iamsystem.constant.ErrorMessage;
import com.example.iamsystem.dto.JwtRefreshTokenDto;
import com.example.iamsystem.dto.JwtResponse;
import com.example.iamsystem.exception.JwtException;
import com.example.iamsystem.security.dto.CheckAccessDto;
import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.security.user.UserDetailsServiceImpl;
import com.example.iamsystem.user.model.dto.UserLoginDto;
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

import java.util.concurrent.atomic.AtomicReference;

import static com.example.iamsystem.constant.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.constant.TokenType.REFRESH_TOKEN;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;


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

    @PostMapping("/check-access")
    public ResponseEntity<Boolean> checkAccess(@Valid @RequestBody CheckAccessDto checkAccessDto) {
        AtomicReference<Boolean> access = new AtomicReference<>(false);
        UserDetails userDetails = userDetailsService.loadUserByUsername(checkAccessDto.getUsername());
        userDetails.getAuthorities().forEach(authority -> {
            if (authority.getAuthority().equals(checkAccessDto.getServiceName() + ":" + checkAccessDto.getAction())) {
                access.set(true);
            }
        });
        return ResponseEntity.ok(access.get());
    }

    private JwtResponse getTokens(UserDetails userDetails, String username) {
        String accessToken = jwtTokenUtil.generateToken(userDetails, ACCESS_TOKEN);
        String refreshToken = jwtTokenUtil.generateToken(userDetails, REFRESH_TOKEN);
        return new JwtResponse(username, refreshToken, accessToken);
    }
}
