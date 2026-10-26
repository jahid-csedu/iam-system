package com.example.iamsystem.security.filter;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.ClientStatus;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.security.config.JwtProperties;
import com.example.iamsystem.security.jwt.SigningKeys;
import com.example.iamsystem.security.jwt.TokenIssuer;
import com.example.iamsystem.security.jwt.TokenVerifier;
import com.example.iamsystem.security.userdetails.ActingTenantResolver;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.session.dto.SessionAccountState;
import com.example.iamsystem.session.entity.UserSession;
import com.example.iamsystem.session.repository.UserSessionRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.user.entity.User;
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
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

    private final JwtProperties properties = new JwtProperties("https://iam.test", List.of());
    private final SigningKeys signingKeys = new SigningKeys(properties);
    private final TokenIssuer tokenIssuer = new TokenIssuer(signingKeys, properties);
    private final AuthenticatedActor actor = new AuthenticatedActor(1L, "alice", 100L, false, 10L);

    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private FilterChain filterChain;
    @Mock
    private ActingTenantResolver actingTenantResolver;
    @Mock
    private HandlerExceptionResolver exceptionResolver;

    private JwtRequestFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private UserSession session;

    @BeforeEach
    void setUp() {
        filter = new JwtRequestFilter(new TokenVerifier(signingKeys), sessionRepository, actingTenantResolver, exceptionResolver);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        Tenant tenant = new Tenant("Acme", TenantStatus.ACTIVE);
        tenant.setId(100L);
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setTenant(tenant);
        Client client = new Client();
        client.setClientId("app-client");
        client.setAccessTokenTtlSeconds(300);
        session = new UserSession();
        session.setId(10L);
        session.setUser(user);
        session.setClient(client);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A valid access token of a live session authenticates the request as that actor")
    void validToken_authenticatesTheActor() throws Exception {
        bearer(tokenIssuer.issueAccessToken(session));
        when(sessionRepository.findActor(eq(10L), eq(1L), any(Instant.class))).thenReturn(Optional.of(actor));
        when(actingTenantResolver.resolve(actor, null)).thenReturn(actor);

        filter.doFilter(request, response, filterChain);

        assertSame(actor, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("A platform user's X-Tenant-Id makes the request work in that tenant")
    void tenantHeader_setsTheActingTenant() throws Exception {
        bearer(tokenIssuer.issueAccessToken(session));
        request.addHeader("X-Tenant-Id", "200");
        AuthenticatedActor acting = actor.actingIn(200L);
        when(sessionRepository.findActor(eq(10L), eq(1L), any(Instant.class))).thenReturn(Optional.of(actor));
        when(actingTenantResolver.resolve(actor, "200")).thenReturn(acting);

        filter.doFilter(request, response, filterChain);

        assertSame(acting, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("An unknown or malformed X-Tenant-Id is answered as an error and stops the request")
    void badTenantHeader_isAnsweredAndStopsTheRequest() throws Exception {
        bearer(tokenIssuer.issueAccessToken(session));
        request.addHeader("X-Tenant-Id", "999");
        DataNotFoundException notFound = new DataNotFoundException("Tenant not found");
        when(sessionRepository.findActor(eq(10L), eq(1L), any(Instant.class))).thenReturn(Optional.of(actor));
        when(actingTenantResolver.resolve(actor, "999")).thenThrow(notFound);

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(), eq(notFound));
        verify(filterChain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("A token of an ended session leaves the request unauthenticated, without a reason")
    void tokenWithoutLiveSession_leavesRequestUnauthenticated() throws Exception {
        bearer(tokenIssuer.issueAccessToken(session));
        when(sessionRepository.findActor(eq(10L), eq(1L), any(Instant.class))).thenReturn(Optional.empty());
        when(sessionRepository.findAccountState(eq(10L), eq(1L), any(Instant.class))).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(JwtRequestFilter.REFUSAL_REASON));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("A live session of a blocked account leaves the request unauthenticated and keeps the reason for the 401")
    void tokenOfABlockedAccount_keepsTheReason() throws Exception {
        bearer(tokenIssuer.issueAccessToken(session));
        when(sessionRepository.findActor(eq(10L), eq(1L), any(Instant.class))).thenReturn(Optional.empty());
        when(sessionRepository.findAccountState(eq(10L), eq(1L), any(Instant.class))).thenReturn(Optional.of(
                new SessionAccountState(false, false, null, TenantStatus.ACTIVE, ClientStatus.ACTIVE)));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("Your account is deactivated", request.getAttribute(JwtRequestFilter.REFUSAL_REASON));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("A malformed token leaves the request unauthenticated without an error")
    void malformedToken_leavesRequestUnauthenticated() throws Exception {
        bearer("not-a-jwt");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(sessionRepository);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("A request without a Bearer token passes through unauthenticated")
    void noBearerToken_passesThrough() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(sessionRepository);
        verify(filterChain).doFilter(request, response);
    }

    private void bearer(String token) {
        request.addHeader("Authorization", "Bearer " + token);
    }
}
