package com.example.iamsystem.platform;

import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.platform.service.PlatformBootstrap;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
 * The platform root working inside a tenant through the normal APIs with {@code X-Tenant-Id}: real logins, real tokens
 * and the real filter against MySQL.
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

    private long platformId;
    private long acmeId;
    private String platformRootToken;
    private String acmeRootToken;

    @BeforeEach
    void setUp() throws Exception {
        PlatformBootstrap bootstrap = new PlatformBootstrap(tenantRepository, predefinedRoleService, clientService,
                userRepository, userFactory, new PlatformProperties(new RootUser("platform-root", PASSWORD, "root@platform.test", null)));
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(null));
        platformId = tenantRepository.findByType(TenantType.PLATFORM).orElseThrow().getId();
        String platformConsole = clientRepository.findAllByTenantIdOrderByName(platformId).getFirst().getClientId();
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
