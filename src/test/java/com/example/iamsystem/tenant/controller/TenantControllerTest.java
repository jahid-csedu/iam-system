package com.example.iamsystem.tenant.controller;

import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.tenant.entity.Tenant;
import com.example.iamsystem.tenant.repository.TenantRepository;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.entity.User;
import com.example.iamsystem.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs without a test transaction so that signup commits and rollbacks behave exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void signup_createsTenantWithItsRootUser_whoCanLogIn() throws Exception {
        signup("Acme", "acme-root", "root@acme.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenant.name").value("Acme"))
                .andExpect(jsonPath("$.tenant.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rootUser.username").value("acme-root"))
                .andExpect(jsonPath("$.rootUser.rootUser").value(true));

        mockMvc.perform(post("/api/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UserLoginDto("acme-root", "Password1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    void signup_withTakenTenantName_isRejected() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());

        signup("Acme", "other-root", "other@acme.com").andExpect(status().isBadRequest());
    }

    @Test
    void signup_whenRootUserIsInvalid_leavesNoTenantBehind() throws Exception {
        signup("Acme", "taken", "root@acme.com").andExpect(status().isCreated());

        signup("Beta", "taken", "root@beta.com").andExpect(status().isBadRequest());

        assertFalse(tenantRepository.existsByName("Beta"));
    }

    @Test
    void signup_withoutTenantName_failsValidation() throws Exception {
        signup(" ", "acme-root", "root@acme.com").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void database_allowsOnlyOneRootUserPerTenant() throws Exception {
        signup("Acme", "acme-root", "root@acme.com").andExpect(status().isCreated());
        Tenant acme = userRepository.findByUsername("acme-root").orElseThrow().getTenant();

        User secondRoot = new User();
        secondRoot.setUsername("second-root");
        secondRoot.setEmail("second@acme.com");
        secondRoot.setTenant(acme);
        secondRoot.setRootUser(true);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(secondRoot));
    }

    private ResultActions signup(String tenantName, String username, String email) throws Exception {
        TenantSignupDto request = new TenantSignupDto(tenantName,
                new UserCreationDto(username, "Password1!", "Root User", email));
        return mockMvc.perform(post("/api/tenants/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }
}
