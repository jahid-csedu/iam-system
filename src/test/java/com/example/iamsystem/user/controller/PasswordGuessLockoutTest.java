package com.example.iamsystem.user.controller;

import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.OwnProfileUpdateDto;
import com.example.iamsystem.user.dto.PasswordChangeDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wrong passwords given by a logged-in user count like failed logins (5 lock the account for 15 minutes), with real
 * logins, tokens and filter against MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class PasswordGuessLockoutTest {

    private static final String PASSWORD = "Password1!";
    private static final String WRONG = "Wrong-password-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String console;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode acme = body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto("Acme", new UserCreationDto("acme-root", PASSWORD, null, "root@acme.com", null))));
        console = acme.get("managementConsole").get("client_id").asString();
        token = login();
    }

    @Test
    @DisplayName("Five wrong old passwords lock the account: the token stops working and login is refused")
    void wrongOldPasswords_lockTheAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            changePassword(WRONG).andExpect(status().isBadRequest());
        }

        send(get("/api/me"), token, null).andExpect(status().isUnauthorized());
        send(post("/api/auth/authenticate"), null, new UserLoginDto(console, "acme-root", PASSWORD)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Wrong current passwords on an email change count towards the same limit as wrong old passwords")
    void wrongCurrentPasswords_countTowardsTheSameLimit() throws Exception {
        for (int i = 0; i < 2; i++) {
            changePassword(WRONG).andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 3; i++) {
            changeEmail(WRONG).andExpect(status().isBadRequest());
        }

        send(get("/api/me"), token, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A correct password in between resets the count; a missing one isn't counted")
    void correctPassword_resetsTheCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            changeEmail(WRONG).andExpect(status().isBadRequest());
        }
        changeEmail(null).andExpect(status().isBadRequest());
        assertEquals(4, failedAttempts());

        changeEmail(PASSWORD).andExpect(status().isOk());
        assertEquals(0, failedAttempts());
        for (int i = 0; i < 4; i++) {
            changePassword(WRONG).andExpect(status().isBadRequest());
        }
        send(get("/api/me"), token, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Parallel wrong guesses are all counted")
    void parallelWrongGuesses_areAllCounted() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(executor.submit(() -> changePassword(WRONG).andReturn().getResponse().getStatus()));
            }
            for (Future<Integer> result : results) {
                assertEquals(400, result.get());
            }
        }

        assertEquals(5, failedAttempts());
        send(get("/api/me"), token, null).andExpect(status().isUnauthorized());
    }

    private ResultActions changePassword(String oldPassword) throws Exception {
        return send(patch("/api/users/password"), token, new PasswordChangeDto(oldPassword, "NewPassword1!"));
    }

    private ResultActions changeEmail(String currentPassword) throws Exception {
        return send(patch("/api/me"), token, new OwnProfileUpdateDto(null, "boss@acme.com", currentPassword));
    }

    private int failedAttempts() {
        return jdbcTemplate.queryForObject("SELECT failed_login_attempts FROM users WHERE username = 'acme-root'", Integer.class);
    }

    private String login() throws Exception {
        return body(send(post("/api/auth/authenticate"), null, new UserLoginDto(console, "acme-root", PASSWORD)))
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
