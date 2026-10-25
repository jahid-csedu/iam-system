package com.example.iamsystem.platform.service;

import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the platform tenant of the super users at startup, the first time the application runs on a database: the
 * tenant, its predefined roles and its management console, the same way signup creates a tenant. Later startups find it
 * and do nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformBootstrap implements ApplicationRunner {

    public static final String PLATFORM_TENANT_NAME = "Platform";

    private final TenantRepository tenantRepository;
    private final PredefinedRoleService predefinedRoleService;
    private final ClientService clientService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tenantRepository.existsByType(TenantType.PLATFORM)) {
            return;
        }
        Tenant platform = new Tenant(PLATFORM_TENANT_NAME, TenantStatus.ACTIVE);
        platform.setType(TenantType.PLATFORM);
        tenantRepository.save(platform);
        predefinedRoleService.createPredefinedRoles(platform);
        clientService.createManagementConsole(platform);
        log.info("Created the platform tenant {} with its roles and management console", platform.getId());
    }
}
