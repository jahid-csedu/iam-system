package com.example.iamsystem.emailverification.controller;

import com.example.iamsystem.client.dto.ClientRequestDto;
import com.example.iamsystem.email.dto.OutgoingEmail;
import com.example.iamsystem.email.service.EmailService;
import com.example.iamsystem.emailverification.dto.EmailVerificationConfirmDto;
import com.example.iamsystem.tenant.dto.TenantSignupDto;
import com.example.iamsystem.user.dto.UserCreationDto;
import com.example.iamsystem.user.dto.UserLoginDto;
import com.example.iamsystem.user.dto.UserUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/users/email-verification} with real logins, tokens and filter against MySQL. Mail is mocked; the code is
 * read from the email handed to it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/sql/delete_user_role.sql")
@Sql(scripts = "/sql/delete_user_role.sql", executionPhase = AFTER_TEST_METHOD)
class EmailVerificationTest {

    private static final String PASSWORD = "Password1!";
    private static final String VERIFICATION = "/api/users/email-verification";
    private static final String CONFIRM = "/api/users/email-verification/confirm";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @MockitoBean
    private EmailService emailService;

    private String acmeConsole;
    private String acmeRoot;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode acme = body(send(post("/api/tenants/signup"), null,
                new TenantSignupDto("Acme", new UserCreationDto("acme-root", PASSWORD, null, "root@acme.com", null))));
        acmeConsole = acme.get("managementConsole").get("client_id").asString();
        acmeRoot = accessToken(acmeConsole, "acme-root");
    }

    @Test
    @DisplayName("A new root starts unverified, verifies with the emailed code, and then has nothing left to verify")
    void root_verifiesTheirAddress() throws Exception {
        send(get("/api/me"), acmeRoot, null).andExpect(jsonPath("$.emailVerified").value(false));

        send(post(VERIFICATION), acmeRoot, null).andExpect(status().isNoContent());
        send(post(CONFIRM), acmeRoot, new EmailVerificationConfirmDto(lastCode()))
                .andExpect(status().isNoContent());

        send(get("/api/me"), acmeRoot, null).andExpect(jsonPath("$.emailVerified").value(true));
        send(post(VERIFICATION), acmeRoot, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Your email address is already verified"));
    }

    @Test
    @DisplayName("A regular user verifies through an application client too, and admins see it in the user")
    void regularUser_verifiesTheirAddress() throws Exception {
        String shop = body(send(post("/api/clients"), acmeRoot, new ClientRequestDto("Shop"))).get("client_id").asString();
        long bobId = body(send(post("/api/users"), acmeRoot, new UserCreationDto("bob", PASSWORD, null, "bob@acme.com", null))
                .andExpect(jsonPath("$.emailVerified").value(false))).get("id").asLong();
        String bob = accessToken(shop, "bob");

        send(post(VERIFICATION), bob, null).andExpect(status().isNoContent());
        send(post(CONFIRM), bob, new EmailVerificationConfirmDto(lastCode())).andExpect(status().isNoContent());

        send(get("/api/users/{id}", bobId), acmeRoot, null).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    @DisplayName("A wrong code is refused, and the fifth wrong guess makes even the right code useless")
    void wrongCodes_areLimited() throws Exception {
        send(post(VERIFICATION), acmeRoot, null).andExpect(status().isNoContent());
        String code = lastCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int guess = 0; guess < 5; guess++) {
            send(post(CONFIRM), acmeRoot, new EmailVerificationConfirmDto(wrong))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Invalid or expired code"));
        }
        send(post(CONFIRM), acmeRoot, new EmailVerificationConfirmDto(code)).andExpect(status().isBadRequest());
        send(get("/api/me"), acmeRoot, null).andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    @DisplayName("Three codes per hour; the fourth request is refused")
    void codes_areLimitedPerHour() throws Exception {
        for (int request = 0; request < 3; request++) {
            send(post(VERIFICATION), acmeRoot, null).andExpect(status().isNoContent());
        }
        send(post(VERIFICATION), acmeRoot, null).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("A new email address is unverified again, and a code sent to the old address no longer works")
    void newEmail_isUnverified() throws Exception {
        long bobId = body(send(post("/api/users"), acmeRoot,
                new UserCreationDto("bob", PASSWORD, null, "bob@acme.com", null))).get("id").asLong();
        send(post(VERIFICATION), acmeRoot, null).andExpect(status().isNoContent());
        String code = lastCode();
        send(post(CONFIRM), acmeRoot, new EmailVerificationConfirmDto(code)).andExpect(status().isNoContent());

        send(patch("/api/me"), acmeRoot, Map.of("email", "new-root@acme.com", "currentPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
        send(patch("/api/users/{id}", bobId), acmeRoot, new UserUpdateDto(null, "robert@acme.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));

        send(post(VERIFICATION), acmeRoot, null).andExpect(status().isNoContent());
        String oldCode = lastCode();
        send(patch("/api/me"), acmeRoot, Map.of("email", "root2@acme.com", "currentPassword", PASSWORD))
                .andExpect(status().isOk());
        send(post(CONFIRM), acmeRoot, new EmailVerificationConfirmDto(oldCode)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Verification needs a login, and a confirmation without a code fails validation")
    void anonymousAndEmptyRequests_areRefused() throws Exception {
        send(post(VERIFICATION), null, null).andExpect(status().isUnauthorized());
        send(post(CONFIRM), acmeRoot, new EmailVerificationConfirmDto(" ")).andExpect(status().isUnprocessableEntity());
    }

    private String lastCode() {
        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailService, atLeastOnce()).sendAfterCommit(email.capture());
        return (String) email.getAllValues().getLast().values().get("code");
    }

    private String accessToken(String clientId, String username) throws Exception {
        return body(send(post("/api/auth/authenticate"), null, new UserLoginDto(clientId, username, PASSWORD))
                .andExpect(status().isOk())).get("access_token").asString();
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
