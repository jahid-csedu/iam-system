package com.example.iamsystem.security.jwt;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.TokenClaim;
import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;

/**
 * Issues the tokens of a login or refresh, signed with RS256 by the current {@link SigningKeys} key (named in the
 * {@code kid} header). {@code iss} names the user's tenant, {@code sub} is the user id (usernames are only unique per
 * tenant), {@code aud} and {@code client_id} name the client the user logged in through, and {@code version} is the
 * user's version, so any change to the user invalidates the tokens issued before it. Lifetimes and the access token's
 * user claims come from the client's token settings.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    static final String CLIENT_ID = "client_id";
    static final String VERSION = "version";
    private static final String TENANT_ISSUER_PATH = "/tenants/";

    private final SigningKeys signingKeys;
    private final JwtProperties properties;

    /**
     * Lives as long as the client's access-token lifetime and carries the user claims the client selected.
     */
    public String issueAccessToken(User user, Client client) {
        JwtBuilder token = baseToken(user, client, client.getAccessTokenTtlSeconds(), ACCESS_TOKEN);
        client.getTokenClaims().forEach(claim -> {
            Object value = claimValue(claim, user);
            if (value != null) {
                token.claim(claim.claimName(), value);
            }
        });
        return token.compact();
    }

    /**
     * Lives as long as the client's refresh-token lifetime; carries no user claims.
     */
    public String issueRefreshToken(User user, Client client) {
        return baseToken(user, client, client.getRefreshTokenTtlSeconds(), REFRESH_TOKEN).compact();
    }

    private String issuerOf(Long tenantId) {
        return properties.issuer() + TENANT_ISSUER_PATH + tenantId;
    }

    private JwtBuilder baseToken(User user, Client client, int lifetimeSeconds, TokenType tokenType) {
        SigningKey key = signingKeys.current();
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(key.kid()).type(tokenType.headerType()).and()
                .issuer(issuerOf(user.getTenant().getId()))
                .subject(String.valueOf(user.getId()))
                .audience().add(client.getClientId()).and()
                .id(UUID.randomUUID().toString())
                .claim(CLIENT_ID, client.getClientId())
                .claim(VERSION, user.getVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(lifetimeSeconds)))
                .signWith(key.privateKey(), Jwts.SIG.RS256);
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
