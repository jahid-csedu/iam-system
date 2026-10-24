package com.example.iamsystem.security.jwt;

import com.example.iamsystem.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenUtilTest {

    private final JwtTokenUtil tokenUtil = new JwtTokenUtil();
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setVersion(3);
    }

    @Test
    @DisplayName("A generated token carries the user id as subject, the client and the user's version")
    void generateToken_carriesUserIdClientAndVersion() {
        Claims claims = tokenUtil.parseClaims(tokenUtil.generateToken(user, "app-client", ACCESS_TOKEN), ACCESS_TOKEN);

        assertEquals("7", claims.getSubject());
        assertEquals(7L, tokenUtil.userIdOf(claims));
        assertEquals("app-client", tokenUtil.clientIdOf(claims));
        assertEquals(3, tokenUtil.versionOf(claims));
        assertFalse(claims.containsKey("authorities"));
    }

    @Test
    @DisplayName("Access and refresh tokens are signed with different keys")
    void tokens_cannotBeUsedAsTheOtherType() {
        String refreshToken = tokenUtil.generateToken(user, "app-client", REFRESH_TOKEN);

        assertThrows(JwtException.class, () -> tokenUtil.parseClaims(refreshToken, ACCESS_TOKEN));
        assertFalse(tokenUtil.isValid(refreshToken, ACCESS_TOKEN));
        assertTrue(tokenUtil.isValid(refreshToken, REFRESH_TOKEN));
    }

    @Test
    @DisplayName("A malformed or blank token is not valid")
    void isValid_malformedToken_isFalse() {
        assertFalse(tokenUtil.isValid("not-a-jwt", ACCESS_TOKEN));
        assertFalse(tokenUtil.isValid("", ACCESS_TOKEN));
    }

    @Test
    @DisplayName("A token is issued for the user only while the user's version is unchanged")
    void isIssuedFor_requiresSameUserAndVersion() {
        Claims claims = tokenUtil.parseClaims(tokenUtil.generateToken(user, "app-client", REFRESH_TOKEN), REFRESH_TOKEN);
        assertTrue(tokenUtil.isIssuedFor(claims, user));

        user.setVersion(4);
        assertFalse(tokenUtil.isIssuedFor(claims, user));

        User other = new User();
        other.setId(8L);
        other.setVersion(3);
        assertFalse(tokenUtil.isIssuedFor(claims, other));
    }

    @Test
    @DisplayName("A subject that isn't a user id is rejected")
    void userIdOf_nonNumericSubject_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> tokenUtil.userIdOf(Jwts.claims().subject("alice").build()));
    }

    @Test
    @DisplayName("A token without a version has no version")
    void versionOf_missingClaim_isNull() {
        assertNull(tokenUtil.versionOf(Jwts.claims().subject("alice").build()));
    }
}
