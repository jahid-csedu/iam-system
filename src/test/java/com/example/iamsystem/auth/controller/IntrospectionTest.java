package com.example.iamsystem.auth.controller;

import com.example.iamsystem.auth.dto.IntrospectionRequest;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/auth/introspect} with real logins and tokens against MySQL, without a test transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class IntrospectionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantRepository tenantRepository;

    private String consoleClientId;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", "Password1!", "Acme Root", "root@acme.com", null));
        consoleClientId = body(postJson("/api/tenants/signup", signup).andExpect(status().isCreated()))
                .get("managementConsole").get("client_id").asString();
        accessToken = body(postJson("/api/auth/authenticate", new UserLoginDto(consoleClientId, "acme-root", "Password1!"))
                .andExpect(status().isOk()))
                .get("access_token").asString();
    }

    @Test
    @DisplayName("A live access token is active, with its user, client, tenant issuer and lifetime")
    void liveToken_isActive() throws Exception {
        introspect(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").value(String.valueOf(root().getId())))
                .andExpect(jsonPath("$.client_id").value(consoleClientId))
                .andExpect(jsonPath("$.aud[0]").value(consoleClientId))
                .andExpect(jsonPath("$.iss").value("http://localhost:8080/tenants/" + root().getTenant().getId()))
                .andExpect(jsonPath("$.username").value("acme-root"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.exp").isNumber())
                .andExpect(jsonPath("$.sid").isNumber());
    }

    @Test
    @DisplayName("After logout the token is inactive and nothing else is revealed")
    void loggedOutToken_isInactive() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        introspect(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sub").doesNotExist());
    }

    @Test
    @DisplayName("A token of a locked user is inactive")
    void tokenOfLockedUser_isInactive() throws Exception {
        User root = root();
        root.setUserLocked(true);
        root.setAccountLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));
        userRepository.save(root);

        introspect(accessToken).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("A token of a suspended tenant's user is inactive")
    void tokenOfSuspendedTenant_isInactive() throws Exception {
        Tenant tenant = tenantRepository.findById(root().getTenant().getId()).orElseThrow();
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        introspect(accessToken).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("A malformed token is inactive, still with status 200")
    void malformedToken_isInactive() throws Exception {
        introspect("not-a-jwt")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("Introspection without a token fails validation")
    void missingToken_failsValidation() throws Exception {
        introspect("").andExpect(status().isUnprocessableEntity());
    }

    private ResultActions introspect(String token) throws Exception {
        return postJson("/api/auth/introspect", new IntrospectionRequest(token));
    }

    private User root() {
        return findByUsername(userRepository, "acme-root").orElseThrow();
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
