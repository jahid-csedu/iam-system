package com.example.iamsystem.platform;

import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.platform.service.PlatformBootstrap;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.UserType;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform users working inside a tenant through the normal APIs with {@code X-Tenant-Id}: the platform root, and a
 * platform user with grants. Real logins, real tokens and the real filter against MySQL. Grants are inserted directly
 * (their endpoints come later).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class ActingTenantTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ClientRepository clientRepository;
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
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long platformId;
    private long acmeId;
    private String platformRootToken;
    private String acmeRootToken;
    private String platformConsole;

    @BeforeEach
    void setUp() throws Exception {
        PlatformBootstrap bootstrap = new PlatformBootstrap(tenantRepository, predefinedRoleService, clientService,
                userRepository, userFactory, new PlatformProperties(new RootUser("platform-root", PASSWORD, "root@platform.test", null)));
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(null));
        platformId = tenantRepository.findByType(TenantType.PLATFORM).orElseThrow().getId();
        platformConsole = clientRepository.findAllByTenantIdOrderByName(platformId).getFirst().getClientId();
        platformRootToken = login(platformConsole, "platform-root");

        JsonNode acme = body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto("Acme", new UserCreationDto("acme-root", PASSWORD, "Acme Root", "root@acme.com", null))));
        acmeId = acme.get("tenant").get("id").asLong();
        acmeRootToken = login(acme.get("managementConsole").get("client_id").asString(), "acme-root");
    }

    @Test
    @DisplayName("With X-Tenant-Id the platform root creates and lists users inside that tenant")
    void platformRoot_worksInsideATenant() throws Exception {
        send(post("/api/users").header("X-Tenant-Id", acmeId), platformRootToken,
                new UserCreationDto("carol", PASSWORD, "Carol", "carol@acme.com", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(acmeId));

        send(get("/api/users").header("X-Tenant-Id", acmeId), platformRootToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", containsInAnyOrder("acme-root", "carol")));
        send(get("/api/users"), platformRootToken, null)
                .andExpect(jsonPath("$.content[*].username", contains("platform-root")));
    }

    @Test
    @DisplayName("A tenant user's X-Tenant-Id is ignored: they keep working in their own tenant")
    void tenantUser_headerIsIgnored() throws Exception {
        send(get("/api/users").header("X-Tenant-Id", platformId), acmeRootToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", contains("acme-root")));
    }

    @Test
    @DisplayName("A malformed X-Tenant-Id is a bad request and an unknown one is not found")
    void badTenantHeader_isRejected() throws Exception {
        send(get("/api/users").header("X-Tenant-Id", "acme"), platformRootToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("X-Tenant-Id must be a tenant id"));
        send(get("/api/users").header("X-Tenant-Id", 999_999L), platformRootToken, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tenant not found"));
    }

    @Test
    @DisplayName("The platform root changes its own password even while working in another tenant")
    void ownPasswordChange_usesTheHomeTenant() throws Exception {
        send(patch("/api/users/password").header("X-Tenant-Id", acmeId), platformRootToken,
                new PasswordChangeDto(PASSWORD, "NewPassword1!"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("The platform root can still work inside a suspended tenant")
    void platformRoot_worksInsideASuspendedTenant() throws Exception {
        send(put("/api/platform/tenants/{id}/suspend", acmeId), platformRootToken, null).andExpect(status().isOk());

        send(get("/api/users").header("X-Tenant-Id", acmeId), platformRootToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", contains("acme-root")));
    }

    @Test
    @DisplayName("Without a grant, another platform user can't do anything inside a tenant")
    void platformUserWithoutGrant_isForbidden() throws Exception {
        String support = createSupportUser();

        send(get("/api/users").header("X-Tenant-Id", acmeId), support, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A role granted on one tenant gives its permissions in that tenant only")
    void roleGrantOnOneTenant_appliesThereOnly() throws Exception {
        String support = createSupportUser();
        long globexId = signupGlobex();
        grantRole("VIEWER", acmeId);

        send(get("/api/users").header("X-Tenant-Id", acmeId), support, null).andExpect(status().isOk());
        send(post("/api/users").header("X-Tenant-Id", acmeId), support,
                new UserCreationDto("dave", PASSWORD, null, "dave@acme.com", null)).andExpect(status().isForbidden());
        send(get("/api/users").header("X-Tenant-Id", globexId), support, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A permission granted on all tenants applies in every standard tenant, but not in the platform tenant")
    void permissionGrantOnAllTenants_appliesEverywhereButHome() throws Exception {
        String support = createSupportUser();
        long globexId = signupGlobex();
        jdbcTemplate.update("""
                INSERT INTO platform_permission_grants (user_id, permission_id, tenant_id, created_at)
                SELECT u.id, p.id, NULL, NOW(6) FROM users u, permissions p WHERE u.username = 'support' AND p.name = 'users:read'
                """);

        send(get("/api/users").header("X-Tenant-Id", acmeId), support, null).andExpect(status().isOk());
        send(get("/api/users").header("X-Tenant-Id", globexId), support, null).andExpect(status().isOk());
        send(get("/api/users"), support, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A platform role may hold tenants:* keys, but a normally assigned role never gives them")
    void platformRoleWithPlatformKey_givesNothingWithoutAGrant() throws Exception {
        String support = createSupportUser();
        long viewerId = roleRepository.getRequiredByNameAndTenantId("VIEWER", platformId).getId();
        long tenantsRead = permissionId("tenants:read");

        send(put("/api/roles/permissions"), platformRootToken, Map.of("roleId", viewerId, "permissionIds", Set.of(tenantsRead)))
                .andExpect(status().isNoContent());
        send(put("/api/users/roles"), platformRootToken, Map.of("username", "support", "roleIds", Set.of(viewerId)))
                .andExpect(status().isNoContent());

        send(get("/api/platform/tenants"), support, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A standard tenant's role can't hold tenants:* keys")
    void standardTenantRole_cannotHoldPlatformKeys() throws Exception {
        long acmeViewerId = roleRepository.getRequiredByNameAndTenantId("VIEWER", acmeId).getId();

        send(put("/api/roles/permissions"), acmeRootToken, Map.of("roleId", acmeViewerId, "permissionIds", Set.of(permissionId("tenants:read"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The platform tenant takes management users only, and no application clients")
    void platformTenant_refusesRegularUsersAndApplicationClients() throws Exception {
        send(post("/api/users"), platformRootToken, new UserCreationDto("plain", PASSWORD, null, "plain@platform.test", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The platform tenant has management users only"));
        send(post("/api/clients"), platformRootToken, Map.of("name", "Shop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The platform tenant has no application clients, only its management console"));
    }

    @Test
    @DisplayName("Inside a standard tenant the platform root still creates regular users and application clients")
    void platformRootInsideATenant_createsRegularUsersAndClients() throws Exception {
        send(post("/api/users").header("X-Tenant-Id", acmeId), platformRootToken,
                new UserCreationDto("plain", PASSWORD, null, "plain@acme.com", null)).andExpect(status().isCreated());
        send(post("/api/clients").header("X-Tenant-Id", acmeId), platformRootToken, Map.of("name", "Shop"))
                .andExpect(status().isCreated());
    }

    private long permissionId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM permissions WHERE name = ?", Long.class, name);
    }

    /**
     * A management user of the platform tenant without roles, logged in; returns the access token.
     */
    private String createSupportUser() throws Exception {
        send(post("/api/users"), platformRootToken,
                new UserCreationDto("support", PASSWORD, "Support", "support@platform.test", UserType.MANAGEMENT))
                .andExpect(status().isCreated());
        return login(platformConsole, "support");
    }

    private long signupGlobex() throws Exception {
        return body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto("Globex", new UserCreationDto("globex-root", PASSWORD, null, "root@globex.com", null))))
                .get("tenant").get("id").asLong();
    }

    private void grantRole(String platformRoleName, long tenantId) {
        long roleId = roleRepository.getRequiredByNameAndTenantId(platformRoleName, platformId).getId();
        jdbcTemplate.update("""
                INSERT INTO platform_role_grants (user_id, role_id, tenant_id, created_at)
                SELECT id, ?, ?, NOW(6) FROM users WHERE username = 'support'
                """, roleId, tenantId);
    }

    private String login(String clientId, String username) throws Exception {
        return body(send(post("/api/auth/authenticate"), null, new UserLoginDto(clientId, username, PASSWORD)))
                .get("access_token").asString();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String accessToken, Object body) throws Exception {
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
