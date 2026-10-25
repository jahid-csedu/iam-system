package com.example.iamsystem.session.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.common.exception.InvalidTokenException;
import com.example.iamsystem.session.entity.RefreshToken;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.session.repository.RefreshTokenRepository;
import com.example.iamsystem.session.repository.UserSessionRepository;
import com.example.iamsystem.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_TOKEN;

/**
 * Login sessions and their single-use refresh tokens. A session lives as long as its client's access tokens, or, when the
 * client issues refresh tokens, until its latest refresh token expires: every refresh replaces the token and extends the
 * session. A refresh token presented a second time means it was copied, so its whole session is revoked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;

    /**
     * Starts the session of a login, with a refresh token when the client issues them.
     */
    @Transactional
    public IssuedSession start(User user, Client client) {
        Instant now = Instant.now();
        UserSession session = new UserSession();
        session.setTenant(user.getTenant());
        session.setUser(user);
        session.setClient(client);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(client.isRefreshTokenEnabled()
                ? client.getRefreshTokenTtlSeconds()
                : client.getAccessTokenTtlSeconds()));
        sessionRepository.save(session);
        return new IssuedSession(session, client.isRefreshTokenEnabled() ? issueRefreshToken(session, now) : null);
    }

    /**
     * The unused, unexpired refresh token behind {@code token}, of a live session started through {@code client}.
     * Runs inside the caller's transaction, which must not roll back on {@link InvalidTokenException}: presenting a
     * used token revokes its session.
     *
     * @throws InvalidTokenException otherwise
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = InvalidTokenException.class)
    public RefreshToken requireRefreshableToken(Client client, String token) {
        Instant now = Instant.now();
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(token))
                .filter(found -> found.getSession().getClient().equals(client))
                .orElseThrow(() -> new InvalidTokenException(INVALID_TOKEN));
        if (refreshToken.getUsedAt() != null) {
            revokeAfterReuse(refreshToken.getSession());
        }
        if (!refreshToken.getExpiresAt().isAfter(now) || !refreshToken.getSession().isLive(now)) {
            throw new InvalidTokenException(INVALID_TOKEN);
        }
        return refreshToken;
    }

    /**
     * Uses up the refresh token and issues its successor, extending the session to the new token's expiry.
     * Same transaction rule as {@link #requireRefreshableToken}.
     *
     * @throws InvalidTokenException if a concurrent request used the token first (its session is then revoked)
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = InvalidTokenException.class)
    public IssuedSession rotate(RefreshToken refreshToken) {
        Instant now = Instant.now();
        UserSession session = refreshToken.getSession();
        if (refreshTokenRepository.markUsed(refreshToken.getId(), now) == 0) {
            revokeAfterReuse(session);
        }
        String successor = issueRefreshToken(session, now);
        session.setExpiresAt(now.plusSeconds(session.getClient().getRefreshTokenTtlSeconds()));
        return new IssuedSession(session, successor);
    }

    /**
     * Logout: ends the session's access and refresh tokens.
     */
    @Transactional
    public void revoke(Long sessionId) {
        sessionRepository.revoke(sessionId, Instant.now());
    }

    /**
     * After a password reset: logs the user out everywhere.
     */
    @Transactional
    public void revokeAllOf(Long userId) {
        int revoked = sessionRepository.revokeAllOfUser(userId, Instant.now());
        log.info("Revoked {} session(s) of user {}", revoked, userId);
    }

    /**
     * After a password change: logs the user out everywhere except the session that made the change.
     */
    @Transactional
    public void revokeOthersOf(Long userId, Long keptSessionId) {
        int revoked = sessionRepository.revokeOtherSessionsOfUser(userId, keptSessionId, Instant.now());
        log.info("Revoked {} other session(s) of user {}", revoked, userId);
    }

    private String issueRefreshToken(UserSession session, Instant now) {
        String token = refreshTokenGenerator.generate();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setSession(session);
        refreshToken.setTokenHash(refreshTokenGenerator.hash(token));
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plusSeconds(session.getClient().getRefreshTokenTtlSeconds()));
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private void revokeAfterReuse(UserSession session) {
        sessionRepository.revoke(session.getId(), Instant.now());
        log.warn("A used refresh token of session {} (user {}) was presented again; session revoked",
                session.getId(), session.getUser().getId());
        throw new InvalidTokenException(INVALID_TOKEN);
    }
}
