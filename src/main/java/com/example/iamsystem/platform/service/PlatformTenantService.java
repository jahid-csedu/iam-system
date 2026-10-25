package com.example.iamsystem.platform.service;

import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.tenant.dto.TenantDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.mapper.TenantMapper;
import com.example.iamsystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.PLATFORM_TENANT_NOT_SUSPENDABLE;
import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_NOT_FOUND;

/**
 * The tenants as super users see and control them: all of them, including the platform tenant. Permission checks are on
 * the endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformTenantService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort BY_ID = Sort.by("id");

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    /**
     * One page of all tenants, ordered by id. Page sizes above {@link #MAX_PAGE_SIZE} are capped;
     * a negative page or a size below 1 is rejected.
     */
    @Transactional(readOnly = true)
    public Page<TenantDto> findTenants(int page, int size) {
        return tenantRepository.findAll(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), BY_ID)).map(tenantMapper::toDto);
    }

    @Transactional(readOnly = true)
    public TenantDto findTenant(Long id) {
        return tenantMapper.toDto(getRequiredTenant(id));
    }

    /**
     * None of the tenant's users can log in, refresh, reset passwords or use their tokens (here or through introspection)
     * until it is activated again. Their sessions are kept. Suspending a suspended tenant changes nothing.
     */
    @Auditable(value = AuditEventType.TENANT_SUSPEND, target = "#id")
    @Transactional
    public TenantDto suspendTenant(Long id) {
        Tenant tenant = getRequiredTenant(id);
        if (tenant.isPlatform()) {
            throw new UserInputNotValidException(PLATFORM_TENANT_NOT_SUSPENDABLE);
        }
        return changeStatus(tenant, TenantStatus.SUSPENDED);
    }

    /**
     * The tenant's users can log in again, and their unexpired sessions work again. Activating an active tenant changes
     * nothing.
     */
    @Auditable(value = AuditEventType.TENANT_ACTIVATE, target = "#id")
    @Transactional
    public TenantDto activateTenant(Long id) {
        return changeStatus(getRequiredTenant(id), TenantStatus.ACTIVE);
    }

    private TenantDto changeStatus(Tenant tenant, TenantStatus status) {
        if (tenant.getStatus() != status) {
            tenant.setStatus(status);
            log.info("Tenant {} is now {}", tenant.getId(), status);
        }
        return tenantMapper.toDto(tenant);
    }

    private Tenant getRequiredTenant(Long id) {
        return tenantRepository.findById(id).orElseThrow(() -> new DataNotFoundException(TENANT_NOT_FOUND));
    }
}
