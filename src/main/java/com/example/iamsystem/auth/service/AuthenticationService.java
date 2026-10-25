package com.example.iamsystem.auth.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.auth.dto.JwtRefreshTokenDto;
import com.example.iamsystem.auth.dto.JwtResponse;
import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.service.ClientResolver;
import com.example.iamsystem.common.exception.InvalidTokenException;
import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.security.jwt.TokenIssuer;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import com.example.iamsystem.security.userdetails.DefaultUserDetails;
import com.example.iamsystem.security.userdetails.DefaultUserDetailsService;
import com.example.iamsystem.session.entity.RefreshToken;
import com.example.iamsystem.session.service.IssuedSession;
import com.example.iamsystem.session.service.SessionService;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.CONSOLE_CLIENT_MANAGEMENT_ONLY;
import static com.example.iamsystem.common.exception.ErrorMessage.REFRESH_TOKENS_DISABLED;

/**
 * Login, token refresh and logout behind the {@code /api/auth} endpoints. Every login and refresh names its
 * client: the client decides the tenant the user is looked up in, and its tokens carry the client. Every login starts a
 * session, which its access tokens name and its refresh tokens keep alive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final DefaultUserDetailsService userDetailsService;
    private final ClientResolver clientResolver;
    private final SessionService sessionService;
    private final TokenIssuer tokenIssuer;
    private final CurrentUserProvider currentUserProvider;

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
        return toResponse(sessionService.start(user, client));
    }

    /**
     * Trades a refresh token, issued through this client, for a new access token and a new refresh token; the old one is
     * used up. The client must still issue refresh tokens and the user must still be able to log in. Commits even when
     * the token is refused, because a reused token revokes its session.
     */
    @Auditable(
            value = AuditEventType.TOKEN_REFRESH,
            detailsExpression = "T(java.util.Map).of('client_id', #request.clientId())"
    )
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public JwtResponse refresh(JwtRefreshTokenDto request) {
        Client client = clientResolver.resolve(request.clientId());
        if (!client.isRefreshTokenEnabled()) {
            throw new UserInputNotValidException(REFRESH_TOKENS_DISABLED);
        }
        RefreshToken refreshToken = sessionService.requireRefreshableToken(client, request.refreshToken());
        userDetailsService.checkUsable(refreshToken.getSession().getUser());
        return toResponse(sessionService.rotate(refreshToken));
    }

    /**
     * Ends the caller's session: its access tokens stop working here and its refresh tokens can't be used.
     */
    @Auditable(AuditEventType.LOGOUT)
    public void logout() {
        sessionService.revoke(currentUserProvider.requireCurrentActor().sessionId());
    }

    private void checkClientAccepts(Client client, User user) {
        if (client.isManagementConsole() && user.getUserType() != UserType.MANAGEMENT) {
            log.warn("Regular user {} tried to log in through the management console", user.getId());
            throw new NoAccessException(CONSOLE_CLIENT_MANAGEMENT_ONLY);
        }
    }

    private JwtResponse toResponse(IssuedSession issued) {
        return new JwtResponse(issued.session().getUser().getUsername(), issued.refreshToken(),
                tokenIssuer.issueAccessToken(issued.session()));
    }
}
