package com.example.iamsystem.platform.service;

import com.example.iamsystem.client.entity.Client;
import com.example.iamsystem.client.entity.ClientType;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.common.exception.UserInputNotValidException;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.role.entity.Role;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the bootstrap against MySQL, with or without a configured platform root. The test configuration has none, so
 * the bootstrap built here gets its configuration explicitly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class PlatformBootstrapTest {

    private static final RootUser CONFIGURED_ROOT =
            new RootUser("platform-root", "Password1!", "root@platform.test", "Platform Root");

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PredefinedRoleService predefinedRoleService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private UserFactory userFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("The platform tenant is created once, active, with its management console and predefined roles")
    void createsThePlatformTenantOnce() {
        run(null);
        run(null);

        Tenant platform = platformTenant();
        assertEquals("Platform", platform.getName());
        assertEquals(TenantStatus.ACTIVE, platform.getStatus());
        assertEquals(List.of(ClientType.MANAGEMENT_CONSOLE),
                clientRepository.findAllByTenantIdOrderByName(platform.getId()).stream().map(Client::getType).toList());
        assertEquals(List.of("ADMIN", "USER_MANAGER", "VIEWER"),
                roleRepository.findAllByTenantIdOrderByName(platform.getId()).stream().map(Role::getName).toList());
    }

    @Test
    @DisplayName("Where a missing root is allowed (local), no or an incomplete configuration leaves the platform without a root")
    void withoutConfiguration_whereAllowed_noPlatformRoot() {
        run(null);
        run(new RootUser("platform-root", null, "root@platform.test", ""));

        assertFalse(userRepository.existsByTenantIdAndIsRootUserTrue(platformTenant().getId()));
    }

    @Test
    @DisplayName("Elsewhere, no or an incomplete configuration stops the startup while the platform has no root")
    void withoutConfiguration_elsewhere_stopsTheStartup() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> run(null, false));
        assertThrows(IllegalStateException.class, () -> run(new RootUser("platform-root", "", "root@platform.test", null), false));

        assertTrue(error.getMessage().startsWith("The platform has no root user"), error.getMessage());
        assertTrue(tenantRepository.findByType(TenantType.PLATFORM).isEmpty());
    }

    @Test
    @DisplayName("Once the platform has its root, the root configuration may be removed")
    void existingRoot_needsNoConfiguration() {
        run(CONFIGURED_ROOT, false);

        assertDoesNotThrow(() -> run(null, false));
    }

    @Test
    @DisplayName("A configured root becomes the platform root, with a verified email, and logs in through the platform console")
    void configuredRoot_isCreatedAndCanLogIn() throws Exception {
        run(CONFIGURED_ROOT);

        Tenant platform = platformTenant();
        User root = userRepository.findByUsernameAndTenantId("platform-root", platform.getId()).orElseThrow();
        assertTrue(root.isRootUser());
        assertTrue(root.isEmailVerified());
        assertEquals(UserType.MANAGEMENT, root.getUserType());
        String consoleClientId = clientRepository.findAllByTenantIdOrderByName(platform.getId()).getFirst().getClientId();
        mockMvc.perform(post("/api/auth/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UserLoginDto(consoleClientId, "platform-root", "Password1!"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An existing platform root is never replaced, even when the configuration changes")
    void existingRoot_isKept() {
        run(CONFIGURED_ROOT);
        run(new RootUser("another-root", "Password1!", "another@platform.test", null));

        assertTrue(userRepository.findByUsernameAndTenantId("platform-root", platformTenant().getId()).isPresent());
        assertFalse(userRepository.existsByUsername("another-root"));
    }

    @Test
    @DisplayName("A configured root breaking the password policy stops the startup, and nothing is created")
    void weakPassword_failsWithoutCreatingAnything() {
        RootUser weak = new RootUser("platform-root", "weak", "root@platform.test", null);

        assertThrows(UserInputNotValidException.class, () -> run(weak));
        assertTrue(tenantRepository.findByType(TenantType.PLATFORM).isEmpty());
    }

    /**
     * Runs a bootstrap with the given root configuration where a missing root is allowed, like the local profile.
     */
    private void run(RootUser root) {
        run(root, true);
    }

    /**
     * Runs a bootstrap with the given root configuration in a transaction, as Spring runs the real one at startup.
     */
    private void run(RootUser root, boolean allowMissingRoot) {
        PlatformBootstrap bootstrap = new PlatformBootstrap(tenantRepository, predefinedRoleService, clientService,
                userRepository, userFactory, new PlatformProperties(root, allowMissingRoot));
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(null));
    }

    private Tenant platformTenant() {
        return tenantRepository.findByType(TenantType.PLATFORM).orElseThrow();
    }
}
