package com.example.iamsystem.session.dto;

import com.example.iamsystem.client.entity.ClientStatus;
import com.example.iamsystem.tenant.entity.TenantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionAccountStateTest {

    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-26T08:15:00Z");

    @Test
    @DisplayName("Each blocked status gives its reason, the first that applies")
    void blockedStatuses_giveTheirReason() {
        assertEquals(Optional.of("Your account is deactivated"),
                new SessionAccountState(false, true, LATER, TenantStatus.SUSPENDED, ClientStatus.SUSPENDED).refusalReason(NOW));
        assertEquals(Optional.of("Your account is locked until 2026-10-26T08:15:00Z"),
                new SessionAccountState(true, true, LATER, TenantStatus.SUSPENDED, ClientStatus.ACTIVE).refusalReason(NOW));
        assertEquals(Optional.of("Tenant is suspended"),
                new SessionAccountState(true, false, null, TenantStatus.SUSPENDED, ClientStatus.SUSPENDED).refusalReason(NOW));
        assertEquals(Optional.of("Client is suspended"),
                new SessionAccountState(true, false, null, TenantStatus.ACTIVE, ClientStatus.SUSPENDED).refusalReason(NOW));
    }

    @Test
    @DisplayName("A lock that has run out blocks nothing, and neither does a usable account")
    void expiredLockOrUsableAccount_givesNoReason() {
        assertEquals(Optional.empty(),
                new SessionAccountState(true, true, NOW.minusSeconds(1), TenantStatus.ACTIVE, ClientStatus.ACTIVE).refusalReason(NOW));
        assertEquals(Optional.empty(),
                new SessionAccountState(true, false, null, TenantStatus.ACTIVE, ClientStatus.ACTIVE).refusalReason(NOW));
    }
}
