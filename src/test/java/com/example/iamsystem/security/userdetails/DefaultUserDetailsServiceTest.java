package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

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
        user.setId(1L);
        user.setUsername("alice");
        user.setActive(true);
    }

    @Test
    @DisplayName("Login finds the user by username inside the given tenant, without Spring authorities")
    void findInTenant_returnsTheTenantsUser() {
        when(userRepository.findByUsernameAndTenantId("alice", 100L)).thenReturn(Optional.of(user));

        DefaultUserDetails userDetails = service.findInTenant("alice", 100L).orElseThrow();

        assertSame(user, userDetails.user());
        assertTrue(userDetails.getAuthorities().isEmpty());
    }

    @Test
    @DisplayName("A username unknown in the tenant finds nothing")
    void findInTenant_unknownUsername_isEmpty() {
        when(userRepository.findByUsernameAndTenantId("alice", 200L)).thenReturn(Optional.empty());

        assertTrue(service.findInTenant("alice", 200L).isEmpty());
    }

    @Test
    @DisplayName("Refresh loads an active, unlocked user by id")
    void requireUsableUser_returnsTheUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertSame(user, service.requireUsableUser(1L).user());
    }

    @Test
    @DisplayName("Refresh rejects a user locked until a future time")
    void requireUsableUser_lockedUser_isRejected() {
        user.setUserLocked(true);
        user.setAccountLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(LockedException.class, () -> service.requireUsableUser(1L));
    }

    @Test
    @DisplayName("Refresh rejects a deactivated user")
    void requireUsableUser_inactiveUser_isRejected() {
        user.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> service.requireUsableUser(1L));
    }

    @Test
    @DisplayName("Refresh for a deleted user is not found")
    void requireUsableUser_unknownUser_isNotFound() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> service.requireUsableUser(9L));
    }
}
