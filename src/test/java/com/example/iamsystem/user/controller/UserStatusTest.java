package com.example.iamsystem.user.controller;

import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /api/users/{id}/deactivate|activate|unlock} with real logins, tokens and filter against MySQL. Mail is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class UserStatusTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RoleRepository roleRepository;
    @MockitoBean
    private EmailService emailService;

    private long acmeId;
    private String acmeConsole;
    private String acmeRoot;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode acme = signup("Acme", "acme-root", "root@acme.com");
        acmeId = acme.get("tenant").get("id").asLong();
        acmeConsole = acme.get("managementConsole").get("client_id").asString();
        acmeRoot = accessToken("acme-root");
    }

    @Test
    @DisplayName("A deactivated user can't log in and their token is refused; activating brings both back")
    void deactivateAndActivate_controlTheUser() throws Exception {
        String viewer = managementUserWithRole("viewer", "VIEWER");
        long viewerId = userId("viewer");

        send(put("/api/users/{id}/deactivate", viewerId), acmeRoot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        send(get("/api/users"), viewer)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your account is deactivated"));
        login(acmeConsole, "viewer").andExpect(status().isForbidden());

        send(put("/api/users/{id}/activate", viewerId), acmeRoot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        send(get("/api/users"), viewer).andExpect(status().isOk());
        login(acmeConsole, "viewer").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deactivating twice and activating an active user change nothing")
    void repeatedChanges_areIdempotent() throws Exception {
        long bobId = createUser("bob", null);

        send(put("/api/users/{id}/activate", bobId), acmeRoot).andExpect(jsonPath("$.active").value(true));
        send(put("/api/users/{id}/deactivate", bobId), acmeRoot).andExpect(status().isOk());
        send(put("/api/users/{id}/deactivate", bobId), acmeRoot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("USER_MANAGER may deactivate, activate and unlock users, VIEWER may not")
    void permissions_comeFromTheRoles() throws Exception {
        long bobId = createUser("bob", null);
        String manager = managementUserWithRole("manager", "USER_MANAGER");
        String viewer = managementUserWithRole("viewer", "VIEWER");

        send(put("/api/users/{id}/deactivate", bobId), viewer).andExpect(status().isForbidden());
        send(put("/api/users/{id}/deactivate", bobId), manager).andExpect(status().isOk());
        send(put("/api/users/{id}/activate", bobId), viewer).andExpect(status().isForbidden());
        send(put("/api/users/{id}/activate", bobId), manager).andExpect(status().isOk());
        send(put("/api/users/{id}/unlock", bobId), viewer).andExpect(status().isForbidden());
        send(put("/api/users/{id}/unlock", bobId), manager).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unlocking ends a lockout at once: login and the user's token work again, and the user shows no lock")
    void unlock_endsTheLockout() throws Exception {
        String viewer = managementUserWithRole("viewer", "VIEWER");
        long viewerId = userId("viewer");
        for (int i = 0; i < 5; i++) {
            send(post("/api/auth/authenticate"), null, new UserLoginDto(acmeConsole, "viewer", "Wrong-pass1!"));
        }
        login(acmeConsole, "viewer").andExpect(status().isForbidden());
        send(get("/api/users/{id}", viewerId), acmeRoot).andExpect(jsonPath("$.lockedUntil").isNotEmpty());

        send(put("/api/users/{id}/unlock", viewerId), acmeRoot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
        send(get("/api/users"), viewer).andExpect(status().isOk());
        login(acmeConsole, "viewer").andExpect(status().isOk());
        send(put("/api/users/{id}/unlock", viewerId), acmeRoot).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nobody deactivates or unlocks themselves or the root user, and users of other tenants are not found")
    void protectedTargets_areRefused() throws Exception {
        String admin = managementUserWithRole("admin", "ADMIN");
        long globexRootId = signup("Globex", "globex-root", "root@globex.com").get("rootUser").get("id").asLong();

        send(put("/api/users/{id}/deactivate", userId("admin")), admin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can't deactivate, activate or unlock your own account"));
        send(put("/api/users/{id}/deactivate", userId("acme-root")), admin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The root user can't be deactivated, activated or unlocked"));
        send(put("/api/users/{id}/unlock", userId("admin")), admin).andExpect(status().isForbidden());
        send(put("/api/users/{id}/unlock", userId("acme-root")), admin).andExpect(status().isForbidden());
        send(put("/api/users/{id}/deactivate", globexRootId), acmeRoot).andExpect(status().isNotFound());
        send(put("/api/users/{id}/unlock", globexRootId), acmeRoot).andExpect(status().isNotFound());
    }

    private long userId(String username) throws Exception {
        return body(send(get("/api/users/by-username").param("username", username), acmeRoot)).get("id").asLong();
    }

    private String managementUserWithRole(String username, String roleName) throws Exception {
        createUser(username, UserType.MANAGEMENT);
        long roleId = roleRepository.getRequiredByNameAndTenantId(roleName, acmeId).getId();
        send(put("/api/users/roles"), acmeRoot, Map.of("username", username, "roleIds", Set.of(roleId)))
                .andExpect(status().isNoContent());
        return accessToken(username);
    }

    private String accessToken(String username) throws Exception {
        return body(login(acmeConsole, username).andExpect(status().isOk())).get("access_token").asString();
    }

    private long createUser(String username, UserType userType) throws Exception {
        return body(send(post("/api/users"), acmeRoot,
                new UserCreationDto(username, PASSWORD, null, username + "@acme.com", userType))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode signup(String tenantName, String rootUsername, String rootEmail) throws Exception {
        return body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto(tenantName, new UserCreationDto(rootUsername, PASSWORD, null, rootEmail, null))));
    }

    private ResultActions login(String clientId, String username) throws Exception {
        return send(post("/api/auth/authenticate"), null, new UserLoginDto(clientId, username, PASSWORD));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String accessToken) throws Exception {
        return send(request, accessToken, null);
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
