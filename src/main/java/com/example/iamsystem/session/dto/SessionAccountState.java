package com.example.iamsystem.session.dto;

import com.example.iamsystem.client.entity.ClientStatus;
import com.example.iamsystem.tenant.entity.TenantStatus;

import java.time.Instant;
import java.util.Optional;

import static com.example.iamsystem.common.exception.ErrorMessage.ACCOUNT_DEACTIVATED;
import static com.example.iamsystem.common.exception.ErrorMessage.ACCOUNT_LOCKED_UNTIL;
import static com.example.iamsystem.common.exception.ErrorMessage.CLIENT_SUSPENDED;
import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_SUSPENDED;

/**
 * What a live session's account looks like right now: the user's status, their tenant's and the session's client's.
 * Read only when the access token was refused although its session is live, to tell its holder why.
 */
public record SessionAccountState(boolean active, boolean userLocked, Instant accountLockedUntil,
                                  TenantStatus tenantStatus, ClientStatus clientStatus) {

    /**
     * Why the account's tokens are refused, the first reason that applies; empty when nothing blocks them.
     */
    public Optional<String> refusalReason(Instant now) {
        if (!active) {
            return Optional.of(ACCOUNT_DEACTIVATED);
        }
        if (userLocked && (accountLockedUntil == null || !accountLockedUntil.isBefore(now))) {
            return Optional.of(ACCOUNT_LOCKED_UNTIL.formatted(accountLockedUntil));
        }
        if (tenantStatus != TenantStatus.ACTIVE) {
            return Optional.of(TENANT_SUSPENDED);
        }
        if (clientStatus != ClientStatus.ACTIVE) {
            return Optional.of(CLIENT_SUSPENDED);
        }
        return Optional.empty();
    }
}
