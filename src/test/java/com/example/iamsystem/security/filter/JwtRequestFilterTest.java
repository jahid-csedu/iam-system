package com.example.iamsystem.security.filter;

import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
import static com.example.iamsystem.security.jwt.TokenType.REFRESH_TOKEN;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

    private final JwtTokenUtil tokenUtil = new JwtTokenUtil();
    private final AuthenticatedActor actor = new AuthenticatedActor(1L, "alice", 100L, false);

    @Mock
    private UserRepository userRepository;
    @Mock
    private FilterChain filterChain;

    private JwtRequestFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User user;

    @BeforeEach
    void setUp() {
        filter = new JwtRequestFilter(tokenUtil, userRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        user = new User();
        user.setUsername("alice");
        user.setVersion(2);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A valid access token of a current user authenticates the request as that actor")
    void validToken_authenticatesTheActor() throws Exception {
        bearer(tokenUtil.generateToken(user, ACCESS_TOKEN));
        when(userRepository.findActor(eq("alice"), eq(2), any(Instant.class))).thenReturn(Optional.of(actor));

        filter.doFilter(request, response, filterChain);

        assertSame(actor, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("A revoked token, or one of an inactive or locked user, leaves the request unauthenticated")
    void tokenWithoutCurrentActor_leavesRequestUnauthenticated() throws Exception {
        bearer(tokenUtil.generateToken(user, ACCESS_TOKEN));
        when(userRepository.findActor(eq("alice"), eq(2), any(Instant.class))).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("A malformed token leaves the request unauthenticated without an error")
    void malformedToken_leavesRequestUnauthenticated() throws Exception {
        bearer("not-a-jwt");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepository);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("A refresh token is not accepted as an access token")
    void refreshToken_isNotAnAccessToken() throws Exception {
        bearer(tokenUtil.generateToken(user, REFRESH_TOKEN));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("A request without a Bearer token passes through unauthenticated")
    void noBearerToken_passesThrough() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findActor(anyString(), anyInt(), any());
        verify(filterChain).doFilter(request, response);
    }

    private void bearer(String token) {
        request.addHeader("Authorization", "Bearer " + token);
    }
}
