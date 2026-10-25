package com.example.iamsystem.platform.controller;

import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.security.userdetails.TestActors.actorOf;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role grants through the API, and their effect inside tenants, against MySQL.
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
    @DisplayName("A role granted on a tenant is listed and works there; once revoked it no longer does")
    void grantRole_worksUntilRevoked() throws Exception {
        long grantId = jsonMapper.readTree(grantRole(platformRoot, support, "VIEWER", acmeId)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.roleName").value("VIEWER"))
                        .andExpect(jsonPath("$.tenantId").value(acmeId))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(platformRoot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleGrants[0].id").value(grantId));
        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").with(supportIn(globexId))).andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/platform/grants/roles/{id}", grantId).with(actingAs(platformRoot)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A role granted without a tenant applies on every standard tenant")
    void grantRoleOnAllTenants_appliesEverywhere() throws Exception {
        grantRole(platformRoot, support, "VIEWER", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        mockMvc.perform(get("/api/users").with(supportIn(acmeId))).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").with(supportIn(globexId))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("The same grant can't be given twice")
    void duplicateGrant_isRejected() throws Exception {
        grantRole(platformRoot, support, "VIEWER", acmeId).andExpect(status().isCreated());
        grantRole(platformRoot, support, "VIEWER", acmeId).andExpect(status().isBadRequest());
        grantRole(platformRoot, support, "VIEWER", null).andExpect(status().isCreated());
        grantRole(platformRoot, support, "VIEWER", null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Only platform roles, on standard tenants, to platform users other than the root and yourself")
    void invalidGrants_areRejected() throws Exception {
        long acmeViewer = roleRepository.getRequiredByNameAndTenantId("VIEWER", acmeId).getId();

        send(post("/api/platform/grants/roles"), actingAs(platformRoot), new RoleGrantRequestDto(support.getId(), acmeViewer, acmeId))
                .andExpect(status().isNotFound());
        grantRole(platformRoot, support, "VIEWER", platformId).andExpect(status().isBadRequest());
        grantRole(platformRoot, support, "VIEWER", 999_999L).andExpect(status().isNotFound());
        grantRole(platformRoot, acmeRoot, "VIEWER", acmeId).andExpect(status().isNotFound());
        grantRole(platformRoot, platformRoot, "VIEWER", acmeId).andExpect(status().isForbidden());

        long admin = roleRepository.getRequiredByNameAndTenantId("ADMIN", platformId).getId();
        send(put("/api/users/roles"), actingAs(platformRoot), Map.of("username", "support", "roleIds", Set.of(admin)))
                .andExpect(status().isNoContent());
        grantRole(support, platformRoot, "VIEWER", acmeId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The platform root already holds every permission on every tenant"));
    }

    @Test
    @DisplayName("Grants are managed only from the platform tenant: a tenant's root can't, even with users:update")
    void tenantRoot_cannotManageGrants() throws Exception {
        grantRole(acmeRoot, support, "VIEWER", acmeId).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/platform/users/{id}/grants", support.getId()).with(actingAs(acmeRoot)))
                .andExpect(status().isForbidden());
    }

    private ResultActions grantRole(User actor, User grantee, String platformRoleName, Long tenantId) throws Exception {
        long roleId = roleRepository.getRequiredByNameAndTenantId(platformRoleName, platformId).getId();
        return send(post("/api/platform/grants/roles"), actingAs(actor), new RoleGrantRequestDto(grantee.getId(), roleId, tenantId));
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
