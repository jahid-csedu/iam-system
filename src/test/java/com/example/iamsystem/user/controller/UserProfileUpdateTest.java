package com.example.iamsystem.user.controller;

import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.passwordreset.dto.PasswordResetRequestDto;
import com.example.iamsystem.passwordreset.repository.PasswordResetOTPRepository;
import com.example.iamsystem.role.repository.RoleRepository;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.dto.UserUpdateDto;
import com.example.iamsystem.user.entity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PATCH /api/users/{id}} with real logins, tokens and filter against MySQL. Mail is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class UserProfileUpdateTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordResetOTPRepository otpRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private EmailService emailService;

    private long acmeId;
    private String acmeConsole;
    private String acmeRoot;
    private String shop;
    private long bobId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode acme = signup("Acme", "acme-root", "root@acme.com");
        acmeId = acme.get("tenant").get("id").asLong();
        acmeConsole = acme.get("managementConsole").get("client_id").asString();
        acmeRoot = login(acmeConsole, "acme-root");
        shop = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("client_id").asString();
        bobId = createUser("bob", "bob@acme.com", null);
    }

    @Test
    @DisplayName("The root changes another user's name and email; fields left out stay unchanged")
    void root_changesNameAndEmail() throws Exception {
        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto("Bob Builder", "builder@acme.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Bob Builder"))
                .andExpect(jsonPath("$.email").value("builder@acme.com"));

        send(patch("/api/users/{id}", bobId), acmeRoot, Map.of("fullName", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").doesNotExist())
                .andExpect(jsonPath("$.email").value("builder@acme.com"));
    }

    @Test
    @DisplayName("A new email deletes the pending reset OTP; a name change keeps it")
    void newEmail_deletesThePendingOtp() throws Exception {
        jdbcTemplate.update("UPDATE users SET email_verified = true WHERE id = ?", bobId);
        send(post("/api/password/reset-request"), null, new PasswordResetRequestDto(shop, "bob")).andExpect(status().isOk());
        assertEquals(1, otpRepository.count());

        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto("Bob", null)).andExpect(status().isOk());
        assertEquals(1, otpRepository.count());

        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto(null, "builder@acme.com")).andExpect(status().isOk());
        assertEquals(0, otpRepository.count());
    }

    @Test
    @DisplayName("users:update alone changes a name but not an email, which also needs users:reset-password")
    void emailChange_needsResetPassword() throws Exception {
        String manager = managementUserWithRole("manager", "USER_MANAGER");

        send(patch("/api/users/{id}", bobId), manager, new UserUpdateDto("Bob", null)).andExpect(status().isOk());
        send(patch("/api/users/{id}", bobId), manager, new UserUpdateDto(null, "evil@example.com"))
                .andExpect(status().isForbidden());
        send(patch("/api/users/{id}", bobId), managementUserWithRole("viewer", "VIEWER"), new UserUpdateDto("Bob", null))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A new email must be free in the tenant and not belong to any tenant's root, but may exist in another tenant")
    void newEmail_followsTheUniquenessRules() throws Exception {
        createUser("carol", "carol@acme.com", null);
        JsonNode globex = signup("Globex", "globex-root", "root@globex.com");
        String globexRoot = login(globex.get("managementConsole").get("client_id").asString(), "globex-root");
        send(post("/api/users"), globexRoot, new UserCreationDto("dave", PASSWORD, null, "dave@globex.com", null))
                .andExpect(status().isCreated());

        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto(null, "carol@acme.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email not available"));
        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto(null, "root@globex.com"))
                .andExpect(status().isBadRequest());
        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto(null, "dave@globex.com"))
                .andExpect(status().isOk());
        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto(null, "DAVE@globex.com"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nobody changes their own profile here or the root's, users of other tenants are not found, and bad emails fail validation")
    void protectedTargets_areRefused() throws Exception {
        String admin = managementUserWithRole("admin", "ADMIN");
        long rootId = userId("acme-root");
        long globexRootId = signup("Globex", "globex-root", "root@globex.com").get("rootUser").get("id").asLong();

        send(patch("/api/users/{id}", userId("admin")), admin, new UserUpdateDto("Me", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Change your own profile through /api/me"));
        send(patch("/api/users/{id}", rootId), admin, new UserUpdateDto("Root", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the root user can change their own profile"));
        send(patch("/api/users/{id}", globexRootId), acmeRoot, new UserUpdateDto("X", null)).andExpect(status().isNotFound());
        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto(null, "not-an-email")).andExpect(status().isUnprocessableEntity());
    }

    private long userId(String username) throws Exception {
        return body(send(get("/api/users/by-username").param("username", username), acmeRoot, null)).get("id").asLong();
    }

    private String managementUserWithRole(String username, String roleName) throws Exception {
        createUser(username, username + "@acme.com", UserType.MANAGEMENT);
        long roleId = roleRepository.getRequiredByNameAndTenantId(roleName, acmeId).getId();
        send(put("/api/users/roles"), acmeRoot, Map.of("username", username, "roleIds", Set.of(roleId)))
                .andExpect(status().isNoContent());
        return login(acmeConsole, username);
    }

    private long createUser(String username, String email, UserType userType) throws Exception {
        return body(send(post("/api/users"), acmeRoot, new UserCreationDto(username, PASSWORD, null, email, userType))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode signup(String tenantName, String rootUsername, String rootEmail) throws Exception {
        return body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto(tenantName, new UserCreationDto(rootUsername, PASSWORD, null, rootEmail, null))));
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
