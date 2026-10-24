package com.example.iamsystem.security.filter;

import com.example.iamsystem.security.jwt.JwtTokenUtil;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
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

import static com.example.iamsystem.security.jwt.TokenType.ACCESS_TOKEN;
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
    private JwtTokenUtil tokenUtil;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", "Password1!", "Acme Root", "root@acme.com"));
        mockMvc.perform(post("/api/tenants/signup").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(signup))).andExpect(status().isCreated());

        String login = mockMvc.perform(post("/api/auth/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UserLoginDto("acme-root", "Password1!"))))
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
    @DisplayName("A token issued before the user changed is revoked")
    void tokenOfChangedUser_isUnauthorized() throws Exception {
        updateRoot(user -> user.setFullName("Renamed Root"));

        getUsers(accessToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token of a deactivated user is rejected")
    void tokenOfInactiveUser_isUnauthorized() throws Exception {
        updateRoot(user -> user.setActive(false));

        getUsers(freshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token of a locked user is rejected while the lock lasts")
    void tokenOfLockedUser_isUnauthorized() throws Exception {
        String token = lockRootAndIssueToken(Instant.now().plus(Duration.ofMinutes(15)));

        getUsers(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token of a user whose lock has expired is accepted")
    void tokenOfUserWithExpiredLock_isAccepted() throws Exception {
        String token = lockRootAndIssueToken(Instant.now().minus(Duration.ofMinutes(1)));

        getUsers(token).andExpect(status().isOk());
    }

    private ResultActions getUsers(String token) throws Exception {
        return mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token));
    }

    /**
     * Changes the root user directly in the database; every save bumps the user's version.
     */
    private void updateRoot(Consumer<User> change) {
        User root = userRepository.findByUsername("acme-root").orElseThrow();
        change.accept(root);
        userRepository.save(root);
    }

    /**
     * A token for the root user's current version, issued by the same code login uses.
     */
    private String freshToken() {
        return tokenUtil.generateToken(userRepository.findByUsername("acme-root").orElseThrow(), ACCESS_TOKEN);
    }

    private String lockRootAndIssueToken(Instant lockedUntil) {
        updateRoot(user -> {
            user.setUserLocked(true);
            user.setAccountLockedUntil(lockedUntil);
        });
        return freshToken();
    }
}
