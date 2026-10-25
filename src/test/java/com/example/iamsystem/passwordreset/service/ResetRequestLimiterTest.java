package com.example.iamsystem.passwordreset.service;

import com.example.iamsystem.passwordreset.entity.PasswordResetRequest;
import com.example.iamsystem.passwordreset.repository.PasswordResetRequestRepository;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetRequestLimiterTest {

    @Mock
    private PasswordResetRequestRepository requestRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ResetRequestLimiter limiter;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
    }

    @Test
    @DisplayName("Under the limit, the request is recorded and allowed, with the user's row locked first")
    void underTheLimit_recordsTheRequest() {
        when(requestRepository.countByUserAndRequestedAtAfter(eq(user), any(Instant.class)))
                .thenReturn((long) ResetRequestLimiter.MAX_REQUESTS_PER_WINDOW - 1);

        assertTrue(limiter.tryRecord(user));

        verify(userRepository).lockById(1L);
        ArgumentCaptor<PasswordResetRequest> saved = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(requestRepository).save(saved.capture());
        assertSame(user, saved.getValue().getUser());
    }

    @Test
    @DisplayName("At the limit, the request is refused and not recorded")
    void atTheLimit_refusesTheRequest() {
        when(requestRepository.countByUserAndRequestedAtAfter(eq(user), any(Instant.class)))
                .thenReturn((long) ResetRequestLimiter.MAX_REQUESTS_PER_WINDOW);

        assertFalse(limiter.tryRecord(user));

        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Requests older than an hour are deleted and no longer count")
    void oldRequests_areDeleted() {
        Instant before = Instant.now();

        limiter.tryRecord(user);

        ArgumentCaptor<Instant> windowStart = ArgumentCaptor.forClass(Instant.class);
        verify(requestRepository).deleteOlderThan(eq(user), windowStart.capture());
        verify(requestRepository).countByUserAndRequestedAtAfter(user, windowStart.getValue());
        assertFalse(windowStart.getValue().isBefore(before.minus(Duration.ofHours(1))));
        assertFalse(windowStart.getValue().isAfter(Instant.now().minus(Duration.ofHours(1))));
    }
}
