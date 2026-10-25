package com.example.iamsystem.tenant;

import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.entity.TenantStatus;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public flows of a suspended tenant's users, through its clients, against MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class TenantSuspensionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TenantRepository tenantRepository;
    @MockitoBean
    private JavaMailSender mailSender;

    private String consoleClientId;
    private String refreshToken;

    @BeforeEach
    void setUp() throws Exception {
        TenantSignupDto signup = new TenantSignupDto("Acme",
                new UserCreationDto("acme-root", "Password1!", "Acme Root", "root@acme.com", null));
        JsonNode signupResult = body(postJson("/api/tenants/signup", signup).andExpect(status().isCreated()));
        consoleClientId = signupResult.get("managementConsole").get("client_id").asString();
        refreshToken = body(login().andExpect(status().isOk())).get("refresh_token").asString();

        Tenant tenant = tenantRepository.findById(signupResult.get("tenant").get("id").asLong()).orElseThrow();
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);
    }

    @Test
    @DisplayName("Users of a suspended tenant can't log in")
    void login_isForbidden() throws Exception {
        login().andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tenant is suspended"));
    }

    @Test
    @DisplayName("Refresh tokens of a suspended tenant's users can't be used")
    void refresh_isForbidden() throws Exception {
        postJson("/api/auth/token/refresh", Map.of("client_id", consoleClientId, "refresh_token", refreshToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Users of a suspended tenant can't request a password reset, and no email is sent")
    void passwordResetRequest_isForbidden() throws Exception {
        postJson("/api/password/reset-request", Map.of("client_id", consoleClientId, "identifier", "acme-root"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mailSender);
    }

    private ResultActions login() throws Exception {
        return postJson("/api/auth/authenticate", new UserLoginDto(consoleClientId, "acme-root", "Password1!"));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
