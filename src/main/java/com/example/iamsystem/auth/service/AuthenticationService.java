package com.example.iamsystem.auth.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.auth.dto.TokenValidationRequest;
import com.example.iamsystem.auth.dto.TokenValidationResponse;
import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.JwtException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.security.userdetails.DefaultUserDetailsService;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.CONSOLE_CLIENT_MANAGEMENT_ONLY;
import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;

/**
 * Login, token refresh and token checks behind the {@code /api/auth} endpoints. Every login and refresh names its client:
 * the client decides the tenant the user is looked up in, and its tokens carry the client.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final DefaultUserDetailsService userDetailsService;
    private final ClientResolver clientResolver;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * The username is looked up only inside the client's tenant; the management console accepts management users only.
     */
    @Auditable(
            value = AuditEventType.USER_LOGIN,
            target = "#request.username()",
            detailsExpression = "T(java.util.Map).of('client_id', #request.clientId())"
    )
    public JwtResponse login(UserLoginDto request) {
        Client client = clientResolver.resolve(request.clientId());
        UsernamePasswordAuthenticationToken loginRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password());
        loginRequest.setDetails(client.getTenant().getId());

        User user = ((DefaultUserDetails) authenticationManager.authenticate(loginRequest).getPrincipal()).user();
        checkClientAccepts(client, user);
        return issueTokens(user, client);
    }

    /**
     * The refresh token must have been issued through this client, for the user's current version.
     */
    @Auditable(
            value = AuditEventType.TOKEN_REFRESH,
            detailsExpression = "T(java.util.Map).of('client_id', #request.clientId())"
    )
    public JwtResponse refresh(JwtRefreshTokenDto request) {
        Client client = clientResolver.resolve(request.clientId());
        Claims claims = parseRefreshToken(request.refreshToken())
                .filter(parsed -> client.getClientId().equals(jwtTokenUtil.clientIdOf(parsed)))
                .orElseThrow(() -> new JwtException(INVALID_TOKEN));

        User user = userDetailsService.requireUsableUser(jwtTokenUtil.userIdOf(claims)).user();
        if (!jwtTokenUtil.isIssuedFor(claims, user)) {
            throw new JwtException(INVALID_TOKEN);
        }
        return issueTokens(user, client);
    }

    public TokenValidationResponse validate(TokenValidationRequest request) {
        return new TokenValidationResponse(jwtTokenUtil.isValid(request.getToken(), ACCESS_TOKEN));
    }

    private void checkClientAccepts(Client client, User user) {
        if (client.isManagementConsole() && user.getUserType() != UserType.MANAGEMENT) {
            log.warn("Regular user {} tried to log in through the management console", user.getId());
            throw new NoAccessException(CONSOLE_CLIENT_MANAGEMENT_ONLY);
        }
    }

    private Optional<Claims> parseRefreshToken(String token) {
        try {
            Claims claims = jwtTokenUtil.parseClaims(token, REFRESH_TOKEN);
            jwtTokenUtil.userIdOf(claims);
            return Optional.of(claims);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private JwtResponse issueTokens(User user, Client client) {
        return new JwtResponse(
                user.getUsername(),
                jwtTokenUtil.generateToken(user, client.getClientId(), REFRESH_TOKEN),
                jwtTokenUtil.generateToken(user, client.getClientId(), ACCESS_TOKEN));
    }
}
