package com.example.iamsystem.platform.service;

import com.example.iamsystem.common.exception.DataNotFoundException;
import com.example.iamsystem.tenant.dto.TenantDto;
import com.example.iamsystem.tenant.mapper.TenantMapper;
import com.example.iamsystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.iamsystem.common.exception.ErrorMessage.TENANT_NOT_FOUND;

/**
 * The tenants as super users see them: all of them, including the platform tenant. Permission checks are on the endpoints.
 */
@Service
@RequiredArgsConstructor
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
        return tenantRepository.findById(id)
                .map(tenantMapper::toDto)
                .orElseThrow(() -> new DataNotFoundException(TENANT_NOT_FOUND));
    }
}
