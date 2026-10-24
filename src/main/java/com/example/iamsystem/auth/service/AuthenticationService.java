package com.example.iamsystem.auth.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.auth.dto.AuthorizationRequest;
import com.example.iamsystem.auth.dto.AuthorizationResponse;
import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.auth.dto.TokenValidationRequest;
import com.example.iamsystem.auth.dto.TokenValidationResponse;
import com.example.iamsystem.authorization.AuthorizationService;
import com.example.iamsystem.common.exception.JwtException;
import com.example.iamsystem.permission.entity.PermissionAction;
import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.security.userdetails.DefaultUserDetailsService;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;

/**
 * Login, token refresh and token checks behind the {@code /api/auth} endpoints.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final DefaultUserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuthorizationService authorizationService;
    private final CurrentUserProvider currentUserProvider;

    @Auditable(
            value = AuditEventType.USER_LOGIN,
            target = "#request.username"
    )
    public JwtResponse login(UserLoginDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        return issueTokens(((DefaultUserDetails) authentication.getPrincipal()).user());
    }

    @Auditable(
            value = AuditEventType.TOKEN_REFRESH,
            target = "#request.username"
    )
    public JwtResponse refresh(JwtRefreshTokenDto request) {
        User user = userDetailsService.loadUserByUsername(request.getUsername()).user();
        if (!isValidRefreshToken(request.getRefreshToken(), user)) {
            throw new JwtException(INVALID_TOKEN);
        }
        return issueTokens(user);
    }

    public TokenValidationResponse validate(TokenValidationRequest request) {
        return new TokenValidationResponse(jwtTokenUtil.isValid(request.getToken(), ACCESS_TOKEN));
    }

    /**
     * Answers, for another application, whether the calling user holds {@code serviceName:action}.
     */
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        boolean authorized = authorizationService.hasPermission(
                currentUserProvider.requireCurrentActor(),
                request.getServiceName(),
                PermissionAction.valueOf(request.getAction()));
        return new AuthorizationResponse(authorized);
    }

    /**
     * The refresh token must be correctly signed, unexpired, and issued for the user's current version.
     */
    private boolean isValidRefreshToken(String token, User user) {
        try {
            return jwtTokenUtil.isIssuedFor(jwtTokenUtil.parseClaims(token, REFRESH_TOKEN), user);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private JwtResponse issueTokens(User user) {
        return new JwtResponse(
                user.getUsername(),
                jwtTokenUtil.generateToken(user, REFRESH_TOKEN),
                jwtTokenUtil.generateToken(user, ACCESS_TOKEN));
    }
}
