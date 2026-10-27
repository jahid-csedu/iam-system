package com.example.iamsystem.security.filter;

import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.passwordreset.dto.PasswordResetRequestDto;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.PasswordChangeDto;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The 401 for a token whose live session belongs to a blocked account says why; other refused tokens only say that a
 * valid one is needed. Real logins, tokens and filter against MySQL; mail is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class TokenRefusalReasonTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private EmailService emailService;

    private long acmeId;
    private String console;
    private String root;
    private String alice;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode acme = body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto("Acme", new UserCreationDto("acme-root", PASSWORD, null, "root@acme.com", null))));
        acmeId = acme.get("tenant").get("id").asLong();
        console = acme.get("managementConsole").get("client_id").asString();
        root = login(console, "acme-root");
        send(post("/api/users"), root, new UserCreationDto("alice", PASSWORD, null, "alice@acme.com", UserType.MANAGEMENT))
                .andExpect(status().isCreated());
        alice = login(console, "alice");
    }

    @Test
    @DisplayName("After five wrong passwords the token's 401 says the account is locked and until when")
    void lockedAccount_saysLockedUntil() throws Exception {
        for (int i = 0; i < 5; i++) {
            send(patch("/api/users/password"), alice, new PasswordChangeDto("Wrong-password-1", "NewPassword1!"))
                    .andExpect(status().isBadRequest());
        }

        send(patch("/api/users/password"), alice, new PasswordChangeDto(PASSWORD, "NewPassword1!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message", startsWith("Your account is locked until ")));
    }

    @Test
    @DisplayName("A deactivated account and a suspended tenant are named as the reason")
    void deactivatedAccountAndSuspendedTenant_areNamed() throws Exception {
        jdbcTemplate.update("UPDATE users SET active = false WHERE username = 'alice'");
        send(get("/api/me"), alice, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your account is deactivated"));

        jdbcTemplate.update("UPDATE tenants SET status = 'SUSPENDED' WHERE id = ?", acmeId);
        send(get("/api/me"), root, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tenant is suspended"));
    }

    @Test
    @DisplayName("A suspended client is named as the reason for its sessions' tokens")
    void suspendedClient_isNamed() throws Exception {
        JsonNode shop = body(send(post("/api/clients"), root, new ClientRequestDto("Shop")));
        send(post("/api/users"), root, new UserCreationDto("bob", PASSWORD, null, "bob@acme.com", null))
                .andExpect(status().isCreated());
        String bob = login(shop.get("client_id").asString(), "bob");
        send(put("/api/clients/{id}/suspend", shop.get("id").asLong()), root, null).andExpect(status().isOk());

        send(patch("/api/users/password"), bob, new PasswordChangeDto(PASSWORD, "NewPassword1!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Client is suspended"));
    }

    @Test
    @DisplayName("Missing, malformed and logged-out tokens only say that a valid token is needed")
    void otherRefusedTokens_giveNoReason() throws Exception {
        send(get("/api/me"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("A valid access token is required"));
        send(get("/api/me"), "not-a-jwt", null)
                .andExpect(jsonPath("$.message").value("A valid access token is required"));
        send(post("/api/auth/logout"), alice, null).andExpect(status().isNoContent());
        send(get("/api/me"), alice, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("A valid access token is required"));
    }

    @Test
    @DisplayName("A blocked account's token doesn't break public endpoints")
    void blockedAccountToken_onAPublicEndpoint_isIgnored() throws Exception {
        String shop = body(send(post("/api/clients"), root, new ClientRequestDto("Shop"))).get("client_id").asString();
        jdbcTemplate.update("UPDATE users SET active = false WHERE username = 'alice'");

        send(post("/api/password/reset-request"), alice, new PasswordResetRequestDto(shop, "acme-root"))
                .andExpect(status().isOk());
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
