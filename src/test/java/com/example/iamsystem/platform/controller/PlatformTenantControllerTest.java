package com.example.iamsystem.platform.controller;

import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.platform.service.PlatformBootstrap;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import com.example.iamsystem.user.service.UserFactory;
import org.junit.jupiter.api.BeforeEach;
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

import static com.example.iamsystem.security.userdetails.TestActors.actingAs;
import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A platform root (created by the bootstrap) and a standard tenant (created by signup), against MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class PlatformTenantControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TenantRepository tenantRepository;
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

    private User platformRoot;
    private User acmeRoot;
    private long acmeId;

    @BeforeEach
    void setUp() throws Exception {
        PlatformBootstrap bootstrap = new PlatformBootstrap(tenantRepository, predefinedRoleService, clientService,
                userRepository, userFactory,
                new PlatformProperties(new RootUser("platform-root", "Password1!", "root@platform.test", null)));
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(null));
        platformRoot = findByUsername(userRepository, "platform-root").orElseThrow();

        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", "Password1!", "Acme Root", "root@acme.com", null));
        mockMvc.perform(post("/api/tenants/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());
        acmeRoot = findByUsername(userRepository, "acme-root").orElseThrow();
        acmeId = acmeRoot.getTenant().getId();
    }

    @Test
    @DisplayName("The platform root lists every tenant, the platform tenant included, with type and status")
    void platformRoot_listsAllTenants() throws Exception {
        mockMvc.perform(get("/api/platform/tenants").with(actingAs(platformRoot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("Platform", "Acme")))
                .andExpect(jsonPath("$.content[*].type", contains("PLATFORM", "STANDARD")))
                .andExpect(jsonPath("$.content[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("The platform root views any tenant by id; an unknown id is not found")
    void platformRoot_viewsATenant() throws Exception {
        mockMvc.perform(get("/api/platform/tenants/{id}", acmeId).with(actingAs(platformRoot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.type").value("STANDARD"));
        mockMvc.perform(get("/api/platform/tenants/{id}", 999_999L).with(actingAs(platformRoot)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A tenant's root can't use the platform endpoints")
    void tenantRoot_isForbidden() throws Exception {
        mockMvc.perform(get("/api/platform/tenants").with(actingAs(acmeRoot))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/platform/tenants/{id}", acmeId).with(actingAs(acmeRoot))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The platform endpoints need a logged-in user")
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/platform/tenants")).andExpect(status().isUnauthorized());
    }
}
