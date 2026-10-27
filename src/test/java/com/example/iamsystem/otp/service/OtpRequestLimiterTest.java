package com.example.iamsystem.otp.service;

import com.example.iamsystem.otp.entity.OtpPurpose;
import com.example.iamsystem.otp.entity.OtpRequest;
import com.example.iamsystem.otp.repository.OtpRequestRepository;
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
class OtpRequestLimiterTest {

    private static final OtpPurpose PURPOSE = OtpPurpose.PASSWORD_RESET;

    @Mock
    private OtpRequestRepository requestRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OtpRequestLimiter limiter;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
    }

    @Test
    @DisplayName("Under the purpose's limit, the request is recorded and allowed, with the user's row locked first")
    void underTheLimit_recordsTheRequest() {
        when(requestRepository.countByUserAndPurposeAndRequestedAtAfter(eq(user), eq(PURPOSE), any(Instant.class)))
                .thenReturn((long) OtpRequestLimiter.MAX_REQUESTS_PER_WINDOW - 1);

        assertTrue(limiter.tryRecord(user, PURPOSE));

        verify(userRepository).lockById(1L);
        ArgumentCaptor<OtpRequest> saved = ArgumentCaptor.forClass(OtpRequest.class);
        verify(requestRepository).save(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertSame(PURPOSE, saved.getValue().getPurpose());
    }

    @Test
    @DisplayName("At the purpose's limit, the request is refused and not recorded")
    void atTheLimit_refusesTheRequest() {
        when(requestRepository.countByUserAndPurposeAndRequestedAtAfter(eq(user), eq(PURPOSE), any(Instant.class)))
                .thenReturn((long) OtpRequestLimiter.MAX_REQUESTS_PER_WINDOW);

        assertFalse(limiter.tryRecord(user, PURPOSE));

        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Requests older than an hour are deleted and no longer count")
    void oldRequests_areDeleted() {
        Instant before = Instant.now();

        limiter.tryRecord(user, PURPOSE);

        ArgumentCaptor<Instant> windowStart = ArgumentCaptor.forClass(Instant.class);
        verify(requestRepository).deleteOlderThan(eq(user), windowStart.capture());
        verify(requestRepository).countByUserAndPurposeAndRequestedAtAfter(user, PURPOSE, windowStart.getValue());
        assertFalse(windowStart.getValue().isBefore(before.minus(Duration.ofHours(1))));
        assertFalse(windowStart.getValue().isAfter(Instant.now().minus(Duration.ofHours(1))));
    }
}
