package com.example.iamsystem.security.userdetails;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.iamsystem.common.exception.ErrorMessage.INVALID_TENANT_HEADER;
import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_NOT_FOUND;

/**
 * Decides which tenant a request works in. A platform user may name another tenant in the {@code X-Tenant-Id} header
 * and then works inside it through the normal APIs (what they may do there is {@code AuthorizationService}'s decision).
 * Tenant users always work in their own tenant: the header is ignored for them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActingTenantResolver {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantRepository tenantRepository;

    /**
     * @param headerValue the {@code X-Tenant-Id} header, or {@code null} if the request has none
     * @throws UserInputNotValidException if a platform user's header isn't a tenant id (400)
     * @throws DataNotFoundException      if no tenant has that id (404)
     */
    public AuthenticatedActor resolve(AuthenticatedActor actor, String headerValue) {
        if (headerValue == null || !tenantRepository.existsByIdAndType(actor.homeTenantId(), TenantType.PLATFORM)) {
            return actor;
        }
        Long tenantId = parseTenantId(headerValue);
        if (!tenantRepository.existsById(tenantId)) {
            throw new DataNotFoundException(TENANT_NOT_FOUND);
        }
        log.debug("Platform user {} works in tenant {}", actor.userId(), tenantId);
        return actor.actingIn(tenantId);
    }

    private static Long parseTenantId(String headerValue) {
        try {
            return Long.valueOf(headerValue.trim());
        } catch (NumberFormatException e) {
            throw new UserInputNotValidException(INVALID_TENANT_HEADER);
        }
    }
}
