package com.example.iamsystem.platform.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.ClientType;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

@SpringBootTest
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class PlatformBootstrapTest {

    @Autowired
    private PlatformBootstrap bootstrap;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("The platform tenant is created once, active, with its management console and predefined roles")
    void createsThePlatformTenantOnce() {
        bootstrap.run(null);
        bootstrap.run(null);

        List<Tenant> platforms = tenantRepository.findAll().stream().filter(tenant -> tenant.getType() == TenantType.PLATFORM).toList();
        assertEquals(1, platforms.size());
        Tenant platform = platforms.getFirst();
        assertEquals("Platform", platform.getName());
        assertEquals(TenantStatus.ACTIVE, platform.getStatus());
        assertEquals(List.of(ClientType.MANAGEMENT_CONSOLE),
                clientRepository.findAllByTenantIdOrderByName(platform.getId()).stream().map(Client::getType).toList());
        assertEquals(List.of("ADMIN", "USER_MANAGER", "VIEWER"),
                roleRepository.findAllByTenantIdOrderByName(platform.getId()).stream().map(Role::getName).toList());
    }
}
