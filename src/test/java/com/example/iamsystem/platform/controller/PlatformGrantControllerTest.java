package com.example.iamsystem.platform.controller;

import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.platform.dto.PermissionGrantRequestDto;
import com.example.iamsystem.platform.dto.RoleGrantRequestDto;
import com.example.iamsystem.platform.service.PlatformBootstrap;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.security.userdetails.TestActors.actorOf;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role and permission grants through the API, and their effect inside tenants and on the platform endpoints, against MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class PlatformGrantControllerTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PredefinedRoleService predefinedRoleService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private UserFactory userFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long platformId;
    private long acmeId;
    private long globexId;
    private User platformRoot;
    private User support;
    private User acmeRoot;

    @BeforeEach
    void setUp() throws Exception {
        PlatformBootstrap bootstrap = new PlatformBootstrap(tenantRepository, predefinedRoleService, clientService,
                userRepository, userFactory, new PlatformProperties(new RootUser("platform-root", PASSWORD, "root@platform.test", null)));
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(null));
        platformId = tenantRepository.findByType(TenantType.PLATFORM).orElseThrow().getId();
        platformRoot = findByUsername(userRepository, "platform-root").orElseThrow();

        send(post("/api/users"), actingAs(platformRoot),
                new UserCreationDto("support", PASSWORD, null, "support@platform.test", UserType.MANAGEMENT))
                .andExpect(status().isCreated());
        support = findByUsername(userRepository, "support").orElseThrow();

        acmeId = signup("Acme", "acme-root");
        globexId = signup("Globex", "globex-root");
        acmeRoot = findByUsername(userRepository, "acme-root").orElseThrow();
    }

    @Test
    @DisplayName("A role granted on a tenant is listed and works there; once removed it no longer does")
    void grantRoles_workUntilRemoved() throws Exception {
        long grantId = jsonMapper.readTree(grantRoles(platformRoot, support, acmeId, "VIEWER")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$[0].roleName").value("VIEWER"))
                        .andExpect(jsonPath("$[0].tenantId").value(acmeId))
                        .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleGrants[0].id").value(grantId));
        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").with(supportIn(globexId))).andExpect(status().isForbidden());

        removeRoles(platformRoot, support, acmeId, "VIEWER").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Removing grants removes only the given roles on the given tenant, and ignores roles not granted")
    void removeRoles_removesOnlyWhatIsNamed() throws Exception {
        grantRoles(platformRoot, support, acmeId, "VIEWER", "USER_MANAGER").andExpect(status().isCreated());
        grantRoles(platformRoot, support, null, "VIEWER").andExpect(status().isCreated());

        removeRoles(platformRoot, support, acmeId, "VIEWER", "ADMIN").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(jsonPath("$.roleGrants.length()").value(2))
                .andExpect(jsonPath("$.roleGrants[?(@.roleName == 'VIEWER')].tenantId", contains(nullValue())))
                .andExpect(jsonPath("$.roleGrants[?(@.roleName == 'USER_MANAGER')].tenantId", contains((int) acmeId)));
    }

    @Test
    @DisplayName("Removing with an unknown role removes nothing, and nobody removes their own grants")
    void invalidRemovals_areRejected() throws Exception {
        grantRoles(platformRoot, support, acmeId, "VIEWER").andExpect(status().isCreated());
        long platformViewer = roleRepository.getRequiredByNameAndTenantId("VIEWER", platformId).getId();

        send(delete("/api/platform/grants/roles"), actingAs(platformRoot),
                new RoleGrantRequestDto(support.getId(), Set.of(platformViewer, 999_999L), acmeId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(jsonPath("$.roleGrants.length()").value(1));
        removeRoles(platformRoot, platformRoot, acmeId, "VIEWER").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Several roles are granted at once, and roles granted without a tenant apply on every standard tenant")
    void grantSeveralRolesOnAllTenants_appliesEverywhere() throws Exception {
        grantRoles(platformRoot, support, null, "VIEWER", "USER_MANAGER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tenantId").doesNotExist());

        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").with(supportIn(globexId))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Granting what the user already has keeps the existing grant instead of adding another")
    void repeatedGrant_keepsTheExistingOne() throws Exception {
        long first = jsonMapper.readTree(grantRoles(platformRoot, support, acmeId, "VIEWER")
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        grantRoles(platformRoot, support, acmeId, "VIEWER", "USER_MANAGER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[?(@.roleName == 'VIEWER')].id", contains((int) first)));
        grantRoles(platformRoot, support, null, "VIEWER").andExpect(status().isCreated());
        grantRoles(platformRoot, support, null, "VIEWER").andExpect(status().isCreated());

        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(jsonPath("$.roleGrants.length()").value(3));
    }

    @Test
    @DisplayName("Only platform roles, all or nothing, on standard tenants, to platform users other than the root and yourself")
    void invalidRoleGrants_areRejected() throws Exception {
        long acmeViewer = roleRepository.getRequiredByNameAndTenantId("VIEWER", acmeId).getId();
        long platformViewer = roleRepository.getRequiredByNameAndTenantId("VIEWER", platformId).getId();

        send(post("/api/platform/grants/roles"), actingAs(platformRoot),
                new RoleGrantRequestDto(support.getId(), Set.of(platformViewer, acmeViewer), acmeId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(jsonPath("$.roleGrants").isEmpty());
        send(post("/api/platform/grants/roles"), actingAs(platformRoot), new RoleGrantRequestDto(support.getId(), Set.of(), acmeId))
                .andExpect(status().isUnprocessableEntity());
        grantRoles(platformRoot, support, platformId, "VIEWER").andExpect(status().isBadRequest());
        grantRoles(platformRoot, support, 999_999L, "VIEWER").andExpect(status().isNotFound());
        grantRoles(platformRoot, acmeRoot, acmeId, "VIEWER").andExpect(status().isNotFound());
        grantRoles(platformRoot, platformRoot, acmeId, "VIEWER").andExpect(status().isForbidden());

        long admin = roleRepository.getRequiredByNameAndTenantId("ADMIN", platformId).getId();
        send(put("/api/users/roles"), actingAs(platformRoot), Map.of("username", "support", "roleIds", Set.of(admin)))
                .andExpect(status().isNoContent());
        grantRoles(support, platformRoot, acmeId, "VIEWER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The platform root already holds every permission on every tenant"));
    }

    @Test
    @DisplayName("Grants are managed only from the platform tenant: a tenant's root can't, even with users:update")
    void tenantRoot_cannotManageGrants() throws Exception {
        grantRoles(acmeRoot, support, acmeId, "VIEWER").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(acmeRoot)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Permissions granted on a tenant are listed and work there; once removed they no longer do")
    void grantPermissions_workUntilRemoved() throws Exception {
        grantPermissions(support, acmeId, "users:read", "roles:read")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[*].permissionName", containsInAnyOrder("users:read", "roles:read")));

        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(jsonPath("$.permissionGrants.length()").value(2))
                .andExpect(jsonPath("$.roleGrants").isEmpty());
        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isOk());
        mockMvc.perform(post("/api/users").with(supportIn(acmeId)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UserCreationDto("dave", PASSWORD, null, "dave@acme.com", null))))
                .andExpect(status().isForbidden());

        send(delete("/api/platform/grants/permissions"), actingAs(platformRoot), new PermissionGrantRequestDto(support.getId(),
                Set.of(permissionId("users:read"), permissionId("roles:read")), acmeId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(jsonPath("$.permissionGrants").isEmpty());
    }

    @Test
    @DisplayName("A tenants:* permission granted on all tenants works on the platform endpoints for every tenant")
    void platformPermissionGrant_worksOnThePlatformEndpoints() throws Exception {
        mockMvc.perform(put("/api/platform/tenants/{id}/suspend", globexId).with(actingAs(support))).andExpect(status().isForbidden());

        grantPermissions(support, null, "tenants:suspend").andExpect(status().isCreated());

        mockMvc.perform(put("/api/platform/tenants/{id}/suspend", globexId).with(actingAs(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("An unknown permission grants nothing, and nobody grants permissions to themselves")
    void invalidPermissionGrants_areRejected() throws Exception {
        send(post("/api/platform/grants/permissions"), actingAs(platformRoot),
                new PermissionGrantRequestDto(support.getId(), Set.of(permissionId("users:read"), 999_999L), acmeId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(jsonPath("$.permissionGrants").isEmpty());
        grantPermissions(platformRoot, acmeId, "users:read").andExpect(status().isForbidden());
    }

    private ResultActions grantPermissions(User grantee, Long tenantId, String... permissions) throws Exception {
        Set<Long> permissionIds = Arrays.stream(permissions).map(this::permissionId).collect(Collectors.toSet());
        return send(post("/api/platform/grants/permissions"), actingAs(platformRoot),
                new PermissionGrantRequestDto(grantee.getId(), permissionIds, tenantId));
    }

    private ResultActions grantRoles(User actor, User grantee, Long tenantId, String... platformRoleNames) throws Exception {
        Set<Long> roleIds = Arrays.stream(platformRoleNames)
                .map(name -> roleRepository.getRequiredByNameAndTenantId(name, platformId).getId())
                .collect(Collectors.toSet());
        return send(post("/api/platform/grants/roles"), actingAs(actor), new RoleGrantRequestDto(grantee.getId(), roleIds, tenantId));
    }

    private ResultActions removeRoles(User actor, User grantee, Long tenantId, String... platformRoleNames) throws Exception {
        Set<Long> roleIds = Arrays.stream(platformRoleNames)
                .map(name -> roleRepository.getRequiredByNameAndTenantId(name, platformId).getId())
                .collect(Collectors.toSet());
        return send(delete("/api/platform/grants/roles"), actingAs(actor), new RoleGrantRequestDto(grantee.getId(), roleIds, tenantId));
    }

    private long permissionId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM permissions WHERE name = ?", Long.class, name);
    }

    /**
     * The support user working inside a tenant, as {@code X-Tenant-Id} makes the JWT filter do.
     */
    private RequestPostProcessor supportIn(long tenantId) {
        AuthenticatedActor acting = actorOf(support).actingIn(tenantId);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(acting, null, List.of()));
    }

    private long signup(String tenantName, String rootUsername) throws Exception {
        TenantSignupDto signup = new TenantSignupDto(tenantName,
                new UserCreationDto(rootUsername, PASSWORD, null, rootUsername + "@" + tenantName.toLowerCase() + ".com", null));
        return jsonMapper.readTree(send(post("/api/tenants/signup"), null, signup).andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("tenant").get("id").asLong();
    }

    private ResultActions send(MockHttpServletRequestBuilder request,
                               RequestPostProcessor actor, Object body) throws Exception {
        if (actor != null) {
            request.with(actor);
        }
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)));
    }
}
