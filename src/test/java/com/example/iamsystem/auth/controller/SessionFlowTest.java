package com.example.iamsystem.auth.controller;

import com.example.iamsystem.email.service.EmailService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login sessions end to end: refresh-token rotation and reuse detection, logout, and the password changes that end
 * sessions. Real logins and tokens against MySQL, without a test transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class SessionFlowTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @MockitoBean
    private EmailService emailService;

    private String consoleClientId;

    @BeforeEach
    void setUp() throws Exception {
        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", PASSWORD, "Acme Root", "root@acme.com", null));
        consoleClientId = body(send(post("/api/tenants/signup"), null, signup).andExpect(status().isCreated()))
                .get("managementConsole").get("client_id").asString();
    }

    @Test
    @DisplayName("Login and refresh answer like an OAuth2 token response, never cached: Bearer type and the lifetime in seconds")
    void tokenResponses_areBearerWithLifetimeAndNotCached() throws Exception {
        ResultActions login = send(post("/api/auth/authenticate"), null, new UserLoginDto(consoleClientId, "acme-root", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        refresh(body(login).get("refresh_token").asString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("A refresh returns a new refresh token, and the old one can't be used again")
    void refresh_rotatesTheRefreshToken() throws Exception {
        JsonNode login = login("acme-root");

        JsonNode refreshed = body(refresh(login.get("refresh_token").asString()).andExpect(status().isOk()));

        assertNotEquals(login.get("refresh_token").asString(), refreshed.get("refresh_token").asString());
        assertFalse(refreshed.get("refresh_token").asString().contains("."));
        getUsers(refreshed.get("access_token").asString()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Reusing a used refresh token ends the whole session, including the tokens issued after it")
    void reusedRefreshToken_endsTheSession() throws Exception {
        String firstRefreshToken = login("acme-root").get("refresh_token").asString();
        JsonNode refreshed = body(refresh(firstRefreshToken).andExpect(status().isOk()));

        refresh(firstRefreshToken).andExpect(status().isUnauthorized());

        refresh(refreshed.get("refresh_token").asString()).andExpect(status().isUnauthorized());
        getUsers(refreshed.get("access_token").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An unknown refresh token answers 401")
    void unknownRefreshToken_isUnauthorized() throws Exception {
        refresh("not-a-refresh-token").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout ends the session's access and refresh tokens, but not the user's other sessions")
    void logout_endsOnlyThatSession() throws Exception {
        JsonNode first = login("acme-root");
        JsonNode second = login("acme-root");

        send(post("/api/auth/logout"), first.get("access_token").asString(), null).andExpect(status().isNoContent());

        getUsers(first.get("access_token").asString()).andExpect(status().isUnauthorized());
        refresh(first.get("refresh_token").asString()).andExpect(status().isUnauthorized());
        getUsers(second.get("access_token").asString()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Logout needs an access token")
    void logout_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Changing your own password ends your other sessions but keeps the one that made the change")
    void ownPasswordChange_endsOtherSessions() throws Exception {
        JsonNode current = login("acme-root");
        JsonNode other = login("acme-root");

        send(patch("/api/users/password"), current.get("access_token").asString(),
                new PasswordChangeDto(PASSWORD, "NewPassword1!")).andExpect(status().isNoContent());

        getUsers(current.get("access_token").asString()).andExpect(status().isOk());
        getUsers(other.get("access_token").asString()).andExpect(status().isUnauthorized());
        refresh(other.get("refresh_token").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A password reset by another user ends every session of the reset user")
    void passwordReset_endsEverySession() throws Exception {
        String root = login("acme-root").get("access_token").asString();
        long bobId = createManager(root, "bob");
        JsonNode bob = login("bob");

        send(post("/api/users/{id}/password-reset", bobId), root, null).andExpect(status().isNoContent());

        getUsers(bob.get("access_token").asString()).andExpect(status().isUnauthorized());
        refresh(bob.get("refresh_token").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A user with open sessions can be deleted")
    void userWithSessions_canBeDeleted() throws Exception {
        String root = login("acme-root").get("access_token").asString();
        long bobId = createManager(root, "bob");
        String bobRefreshToken = login("bob").get("refresh_token").asString();

        send(delete("/api/users/{id}", bobId), root, null).andExpect(status().isNoContent());

        refresh(bobRefreshToken).andExpect(status().isUnauthorized());
    }

    private long createManager(String rootToken, String username) throws Exception {
        UserCreationDto request = new UserCreationDto(username, PASSWORD, username, username + "@acme.com", UserType.MANAGEMENT);
        return body(send(post("/api/users"), rootToken, request).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode login(String username) throws Exception {
        return body(send(post("/api/auth/authenticate"), null, new UserLoginDto(consoleClientId, username, PASSWORD))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/token/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"client_id\":\"" + consoleClientId + "\",\"refresh_token\":\"" + refreshToken + "\"}"));
    }

    private ResultActions getUsers(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + accessToken));
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
