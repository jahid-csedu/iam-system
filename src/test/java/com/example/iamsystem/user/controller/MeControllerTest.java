package com.example.iamsystem.user.controller;

import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.client.repository.ClientRepository;
import com.example.iamsystem.client.service.ClientService;
import com.example.iamsystem.permission.PermissionKey;
import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.platform.config.PlatformProperties.RootUser;
import com.example.iamsystem.platform.service.PlatformBootstrap;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.role.service.PredefinedRoleService;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.TenantType;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.OwnProfileUpdateDto;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static com.example.iamsystem.permission.entity.PermissionScope.TENANT;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET} and {@code PATCH /api/me} with real logins, tokens and filter against MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class MeControllerTest {

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
    private RoleRepository roleRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private long acmeId;
    private String acmeConsole;
    private String acmeRoot;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode acme = body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto("Acme", new UserCreationDto("acme-root", PASSWORD, "Acme Root", "root@acme.com", null))));
        acmeId = acme.get("tenant").get("id").asLong();
        acmeConsole = acme.get("managementConsole").get("client_id").asString();
        acmeRoot = login(acmeConsole, "acme-root");
    }

    @Test
    @DisplayName("A tenant's root sees their profile, their tenant and every TENANT permission key")
    void root_seesEveryTenantKey() throws Exception {
        String[] tenantKeys = Arrays.stream(PermissionKey.values())
                .filter(permission -> permission.scope() == TENANT).map(PermissionKey::key).sorted().toArray(String[]::new);

        send(get("/api/me"), acmeRoot, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("acme-root"))
                .andExpect(jsonPath("$.fullName").value("Acme Root"))
                .andExpect(jsonPath("$.email").value("root@acme.com"))
                .andExpect(jsonPath("$.userType").value("MANAGEMENT"))
                .andExpect(jsonPath("$.rootUser").value(true))
                .andExpect(jsonPath("$.homeTenant.id").value(acmeId))
                .andExpect(jsonPath("$.homeTenant.name").value("Acme"))
                .andExpect(jsonPath("$.workingTenant.id").value(acmeId))
                .andExpect(jsonPath("$.workingTenant.status").value("ACTIVE"))
                .andExpect(jsonPath("$.permissions", contains(tenantKeys)));
    }

    @Test
    @DisplayName("A management user sees the keys of their roles, and a role change shows at once")
    void managementUser_seesTheirRoleKeys() throws Exception {
        send(post("/api/users"), acmeRoot, new UserCreationDto("alice", PASSWORD, null, "alice@acme.com", UserType.MANAGEMENT))
                .andExpect(status().isCreated());
        String alice = login(acmeConsole, "alice");
        send(get("/api/me"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootUser").value(false))
                .andExpect(jsonPath("$.permissions", hasSize(0)));

        long viewerId = roleRepository.getRequiredByNameAndTenantId("VIEWER", acmeId).getId();
        send(put("/api/users/roles"), acmeRoot, Map.of("username", "alice", "roleIds", Set.of(viewerId)))
                .andExpect(status().isNoContent());

        send(get("/api/me"), alice, null)
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("users:read", "roles:read", "clients:read", "permissions:read")));
    }

    @Test
    @DisplayName("A regular user is refused and an anonymous caller is unauthorized")
    void regularAndAnonymous_areRefused() throws Exception {
        String shop = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("client_id").asString();
        send(post("/api/users"), acmeRoot, new UserCreationDto("bob", PASSWORD, null, "bob@acme.com", null))
                .andExpect(status().isCreated());

        send(get("/api/me"), login(shop, "bob"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only management users can use this endpoint"));
        send(get("/api/me"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("With X-Tenant-Id the platform root sees the Platform as home, that tenant as working tenant, and every key")
    void platformRoot_insideATenant_seesThatTenant() throws Exception {
        PlatformBootstrap bootstrap = new PlatformBootstrap(tenantRepository, predefinedRoleService, clientService,
                userRepository, userFactory, new PlatformProperties(new RootUser("platform-root", PASSWORD, "root@platform.test", null), false));
        transactionTemplate.executeWithoutResult(status -> bootstrap.run(null));
        long platformId = tenantRepository.findByType(TenantType.PLATFORM).orElseThrow().getId();
        String platformRoot = login(clientRepository.findAllByTenantIdOrderByName(platformId).getFirst().getClientId(), "platform-root");

        send(get("/api/me").header("X-Tenant-Id", acmeId), platformRoot, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTenant.id").value(platformId))
                .andExpect(jsonPath("$.homeTenant.type").value("PLATFORM"))
                .andExpect(jsonPath("$.workingTenant.id").value(acmeId))
                .andExpect(jsonPath("$.workingTenant.name").value("Acme"))
                .andExpect(jsonPath("$.permissions", hasSize(PermissionKey.values().length)));
    }

    @Test
    @DisplayName("You change your own name freely, and your email only with your current password")
    void ownProfile_emailNeedsTheCurrentPassword() throws Exception {
        send(patch("/api/me"), acmeRoot, new OwnProfileUpdateDto("Acme Boss", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Acme Boss"))
                .andExpect(jsonPath("$.permissions").isNotEmpty());

        send(patch("/api/me"), acmeRoot, new OwnProfileUpdateDto(null, "boss@acme.com", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Changing your email needs your current password, and it was missing or wrong"));
        send(patch("/api/me"), acmeRoot, new OwnProfileUpdateDto(null, "boss@acme.com", "Wrong-password-1"))
                .andExpect(status().isBadRequest());
        send(patch("/api/me"), acmeRoot, new OwnProfileUpdateDto(null, "boss@acme.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("boss@acme.com"));
    }

    @Test
    @DisplayName("A root's own new email must be unused in every tenant; a management user's only in theirs and by every root")
    void ownEmail_followsTheUniquenessRules() throws Exception {
        JsonNode globex = body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto("Globex", new UserCreationDto("globex-root", PASSWORD, null, "root@globex.com", null))));
        String globexRoot = login(globex.get("managementConsole").get("client_id").asString(), "globex-root");
        send(post("/api/users"), globexRoot, new UserCreationDto("dave", PASSWORD, null, "dave@globex.com", null))
                .andExpect(status().isCreated());
        send(post("/api/users"), acmeRoot, new UserCreationDto("alice", PASSWORD, null, "alice@acme.com", UserType.MANAGEMENT))
                .andExpect(status().isCreated());
        String alice = login(acmeConsole, "alice");

        send(patch("/api/me"), acmeRoot, new OwnProfileUpdateDto(null, "dave@globex.com", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email not available"));
        send(patch("/api/me"), alice, new OwnProfileUpdateDto(null, "root@globex.com", PASSWORD))
                .andExpect(status().isBadRequest());
        send(patch("/api/me"), alice, new OwnProfileUpdateDto(null, "dave@globex.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dave@globex.com"));
    }

    @Test
    @DisplayName("A regular user can't change their profile here")
    void ownProfile_regularUser_isRefused() throws Exception {
        String shop = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("client_id").asString();
        send(post("/api/users"), acmeRoot, new UserCreationDto("bob", PASSWORD, null, "bob@acme.com", null))
                .andExpect(status().isCreated());

        send(patch("/api/me"), login(shop, "bob"), new OwnProfileUpdateDto("Bob", null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only management users can use this endpoint"));
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
