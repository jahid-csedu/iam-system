package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues access tokens, signed with RS256 by the current {@link SigningKeys} key (named in the {@code kid} header).
 * {@code iss} names the user's tenant, {@code sub} is the user id (usernames are only unique per tenant), {@code aud} and
 * {@code client_id} name the client the user logged in through, and {@code sid} is the session, so revoking the session
 * ends the token. The lifetime and the user claims come from the client's token settings.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    static final String ACCESS_TOKEN_TYPE = "at+jwt";
    static final String CLIENT_ID = "client_id";
    static final String SESSION_ID = "sid";
    private static final String TENANT_ISSUER_PATH = "/tenants/";

    private final SigningKeys signingKeys;
    private final JwtProperties properties;

    public IssuedAccessToken issueAccessToken(UserSession session) {
        User user = session.getUser();
        Client client = session.getClient();
        SigningKey key = signingKeys.current();
        Instant now = Instant.now();
        long expiresIn = client.getAccessTokenTtlSeconds();
        JwtBuilder token = Jwts.builder()
                .header().keyId(key.kid()).type(ACCESS_TOKEN_TYPE).and()
                .issuer(properties.issuer() + TENANT_ISSUER_PATH + user.getTenant().getId())
                .subject(String.valueOf(user.getId()))
                .audience().add(client.getClientId()).and()
                .id(UUID.randomUUID().toString())
                .claim(CLIENT_ID, client.getClientId())
                .claim(SESSION_ID, session.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresIn)));
        client.getTokenClaims().forEach(claim -> {
            Object value = claimValue(claim, user);
            if (value != null) {
                token.claim(claim.claimName(), value);
            }
        });
        return new IssuedAccessToken(token.signWith(key.privateKey(), Jwts.SIG.RS256).compact(), expiresIn);
    }

    private static Object claimValue(TokenClaim claim, User user) {
        return switch (claim) {
            case USERNAME -> user.getUsername();
            case EMAIL -> user.getEmail();
            case FULL_NAME -> user.getFullName();
            case USER_TYPE -> user.getUserType().name();
            case TENANT_ID -> user.getTenant().getId();
        };
    }
}
