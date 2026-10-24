package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DefaultUserDetailsService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("alice");
    }

    @Test
    @DisplayName("An existing, unlocked user is loaded without Spring authorities")
    void loadUserByUsername_returnsTheUser() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        DefaultUserDetails userDetails = service.loadUserByUsername("alice");

        assertSame(user, userDetails.user());
        assertTrue(userDetails.getAuthorities().isEmpty());
    }

    @Test
    @DisplayName("An unknown username is rejected")
    void loadUserByUsername_unknownUser_isRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }

    @Test
    @DisplayName("A user locked until a future time is rejected")
    void loadUserByUsername_lockedUser_isRejected() {
        user.setUserLocked(true);
        user.setAccountLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThrows(LockedException.class, () -> service.loadUserByUsername("alice"));
    }

    @Test
    @DisplayName("A user whose lock has expired is loaded")
    void loadUserByUsername_expiredLock_isLoaded() {
        user.setUserLocked(true);
        user.setAccountLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertSame(user, service.loadUserByUsername("alice").user());
    }
}
