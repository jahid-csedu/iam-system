package com.example.iamsystem.security.service;

import com.example.iamsystem.security.config.LockoutProperties;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailedPasswordCounterTest {

    @Mock
    private UserRepository userRepository;

    private FailedPasswordCounter counter;

    @BeforeEach
    void setUp() {
        counter = new FailedPasswordCounter(userRepository, new LockoutProperties(5, 15));
    }

    @Test
    @DisplayName("A failure below the limit is only counted")
    void recordFailure_belowTheLimit_onlyCounts() {
        when(userRepository.findFailedLoginAttempts(7L)).thenReturn(4);

        counter.recordFailure(7L);

        verify(userRepository).incrementFailedLoginAttempts(7L);
        verify(userRepository, never()).updateAccountLockStatus(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("The failure that reaches the limit locks the account for the lockout duration")
    void recordFailure_atTheLimit_locksTheAccount() {
        when(userRepository.findFailedLoginAttempts(7L)).thenReturn(5);
        Instant before = Instant.now();

        counter.recordFailure(7L);

        ArgumentCaptor<Instant> lockedUntil = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).updateAccountLockStatus(eq(7L), eq(true), lockedUntil.capture());
        assertFalse(lockedUntil.getValue().isBefore(before.plus(Duration.ofMinutes(15))));
    }

    @Test
    @DisplayName("A success clears the count and any lock, in the database and on the entity")
    void recordSuccess_clearsTheCount() {
        User user = new User();
        user.setId(7L);
        user.setFailedLoginAttempts(3);
        user.setUserLocked(true);
        user.setAccountLockedUntil(Instant.now().minusSeconds(60));

        counter.recordSuccess(user);

        verify(userRepository).clearFailedLogins(7L);
        assertEquals(0, user.getFailedLoginAttempts());
        assertFalse(user.isUserLocked());
        assertNull(user.getAccountLockedUntil());
    }

    @Test
    @DisplayName("A success with nothing counted writes nothing")
    void recordSuccess_withNothingCounted_writesNothing() {
        User user = new User();
        user.setId(7L);

        counter.recordSuccess(user);

        verifyNoInteractions(userRepository);
    }
}
