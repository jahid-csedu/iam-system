package com.example.iamsystem.security.filter;

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
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import static com.example.iamsystem.user.repository.TestUserLookup.findByUsername;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real login, real Bearer tokens and the real filter against MySQL. Runs without a test transaction,
 * like production requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantRepository tenantRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", "Password1!", "Acme Root", "root@acme.com", null));
        String signupResponse = mockMvc.perform(post("/api/tenants/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String consoleClientId = jsonMapper.readTree(signupResponse).get("managementConsole").get("client_id").asString();

        String login = mockMvc.perform(post("/api/auth/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UserLoginDto(consoleClientId, "acme-root", "Password1!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = jsonMapper.readTree(login).get("access_token").asString();
    }

    @Test
    @DisplayName("A valid access token authenticates the request")
    void validToken_isAccepted() throws Exception {
        getUsers(accessToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A malformed access token answers 401, not a server error")
    void malformedToken_isUnauthorized() throws Exception {
        getUsers("not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Changing a user's profile keeps their session: permissions are read live, not from the token")
    void tokenOfChangedUser_staysValid() throws Exception {
        updateRoot(user -> user.setFullName("Renamed Root"));

        getUsers(accessToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A token of a deactivated user is rejected")
    void tokenOfInactiveUser_isUnauthorized() throws Exception {
        updateRoot(user -> user.setActive(false));

        getUsers(accessToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token of a locked user is rejected while the lock lasts")
    void tokenOfLockedUser_isUnauthorized() throws Exception {
        lockRoot(Instant.now().plus(Duration.ofMinutes(15)));

        getUsers(accessToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token of a user whose lock has expired is accepted")
    void tokenOfUserWithExpiredLock_isAccepted() throws Exception {
        lockRoot(Instant.now().minus(Duration.ofMinutes(1)));

        getUsers(accessToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A token of a suspended tenant's user is rejected")
    void tokenOfSuspendedTenant_isUnauthorized() throws Exception {
        Tenant tenant = tenantRepository.findById(findByUsername(userRepository, "acme-root").orElseThrow().getTenant().getId())
                .orElseThrow();
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        getUsers(accessToken).andExpect(status().isUnauthorized());
    }

    private ResultActions getUsers(String token) throws Exception {
        return mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token));
    }

    private void updateRoot(Consumer<User> change) {
        User root = findByUsername(userRepository, "acme-root").orElseThrow();
        change.accept(root);
        userRepository.save(root);
    }

    private void lockRoot(Instant lockedUntil) {
        updateRoot(user -> {
            user.setUserLocked(true);
            user.setAccountLockedUntil(lockedUntil);
        });
    }
}
