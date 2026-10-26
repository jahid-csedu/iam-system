package com.example.iamsystem.platform.service;

import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets up the platform level at startup, the same way signup sets up a tenant. The first time the application runs on a
 * database it creates the platform tenant with its predefined roles and management console. While the platform tenant
 * has no root user, it creates one from {@link PlatformProperties}. Without that configuration the startup stops, except
 * in the local profile, where there is no super user yet and a warning says so. An invalid configuration (weak password,
 * username or email already used by a root) stops the startup too.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformBootstrap implements ApplicationRunner {

    public static final String PLATFORM_TENANT_NAME = "Platform";
    private static final String NO_ROOT = "The platform has no root user: set IAM_PLATFORM_ROOT_USERNAME, "
            + "IAM_PLATFORM_ROOT_PASSWORD and IAM_PLATFORM_ROOT_EMAIL";

    private final TenantRepository tenantRepository;
    private final PredefinedRoleService predefinedRoleService;
    private final ClientService clientService;
    private final UserRepository userRepository;
    private final UserFactory userFactory;
    private final PlatformProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Tenant platform = tenantRepository.findByType(TenantType.PLATFORM).orElseGet(this::createPlatformTenant);
        if (!userRepository.existsByTenantIdAndIsRootUserTrue(platform.getId())) {
            createPlatformRoot(platform);
        }
    }

    private Tenant createPlatformTenant() {
        Tenant platform = new Tenant(PLATFORM_TENANT_NAME, TenantStatus.ACTIVE);
        platform.setType(TenantType.PLATFORM);
        tenantRepository.save(platform);
        predefinedRoleService.createPredefinedRoles(platform);
        String consoleClientId = clientService.createManagementConsole(platform).clientId();
        log.info("Created the platform tenant {} with its roles and management console (client_id {})",
                platform.getId(), consoleClientId);
        return platform;
    }

    private void createPlatformRoot(Tenant platform) {
        RootUser root = properties.root();
        if (root == null || !root.isComplete()) {
            if (!properties.allowMissingRoot()) {
                throw new IllegalStateException(NO_ROOT + " (needed until the platform has its root user)");
            }
            log.warn("{} to create one at the next startup", NO_ROOT);
            return;
        }
        UserCreationDto request = new UserCreationDto(root.username(), root.password(), root.fullName(), root.email(), null);
        User platformRoot = userRepository.save(userFactory.newRootUser(request, platform));
        log.info("Created the platform root user {} ('{}')", platformRoot.getId(), platformRoot.getUsername());
    }
}
